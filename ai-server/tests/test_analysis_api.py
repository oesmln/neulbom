from pathlib import Path
from uuid import UUID, uuid4

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.errors import (
    register_exception_handlers,
)
from app.api.routes.analyses import (
    get_analysis_idempotency_service,
    router,
)
from app.api.schemas.analysis import (
    EXPECTED_QUESTION_CODES,
)
from app.contracts.loader import (
    load_contract_bundle,
)
from app.contracts.models import ContractBundle
from app.core.config import (
    PROJECT_ROOT,
    Settings,
    get_settings,
)
from app.core.runtime import RuntimeState
from app.repositories.analysis import (
    SQLiteAnalysisRepository,
)
from app.repositories.idempotency import (
    SQLiteIdempotencyRepository,
)
from app.services.idempotency import (
    IdempotencyService,
)

SERVICE_TOKEN = (
    "test-service-token-with-at-least-32-characters"
)
IDEMPOTENCY_KEY = (
    "analysis-create-key-0001"
)


class FakeWorker:
    def __init__(self) -> None:
        self.is_running = True
        self.enqueued_ids: list[UUID] = []

    async def enqueue(
        self,
        analysis_id: UUID,
    ) -> None:
        self.enqueued_ids.append(
            analysis_id,
        )


class FakeClipWorker:
    is_running = True

    def __init__(self) -> None:
        self.requests = []

    async def enqueue(self, request) -> bool:
        self.requests.append(request)
        return True


def create_test_client(
    tmp_path: Path,
) -> tuple[
    TestClient,
    FakeWorker,
    SQLiteAnalysisRepository,
    ContractBundle,
]:
    contracts = load_contract_bundle(
        PROJECT_ROOT / "contracts",
    )
    repository = SQLiteAnalysisRepository(
        tmp_path / "analyses.sqlite3",
    )
    worker = FakeWorker()

    application = FastAPI()
    register_exception_handlers(
        application,
    )
    application.include_router(
        router,
    )
    application.state.runtime_state = (
        RuntimeState(
            contract_bundle=contracts,
            analysis_repository=repository,
            analysis_worker=worker,
            clip_prefetch_worker=FakeClipWorker(),
        )
    )

    settings = Settings(
        _env_file=None,
        service_token=SERVICE_TOKEN,
        analysis_db_path=(
            tmp_path / "analyses.sqlite3"
        ),
        idempotency_db_path=(
            tmp_path / "idempotency.sqlite3"
        ),
    )
    idempotency_service = (
        IdempotencyService(
            SQLiteIdempotencyRepository(
                tmp_path
                / "idempotency.sqlite3",
            ),
        )
    )

    application.dependency_overrides[
        get_settings
    ] = lambda: settings
    application.dependency_overrides[
        get_analysis_idempotency_service
    ] = lambda: idempotency_service

    return (
        TestClient(application),
        worker,
        repository,
        contracts,
    )


def create_request_payload(
    contracts: ContractBundle,
    *,
    analysis_id: UUID | None = None,
    assessment_id: UUID | None = None,
) -> dict:
    analysis_id = analysis_id or uuid4()
    assessment_id = assessment_id or uuid4()
    responses: list[dict] = []

    for question in sorted(
        contracts.cist.questions,
        key=lambda item: item.order,
    ):
        if (
            question.administration_mode
            == "conditional"
        ):
            responses.append(
                {
                    "question_code": (
                        question.question_code
                    ),
                    "variant_id": (
                        question.variant_id
                    ),
                    "administration_status": (
                        "not_applicable"
                    ),
                },
            )
            continue

        responses.append(
            {
                "question_code": (
                    question.question_code
                ),
                "variant_id": (
                    question.variant_id
                ),
                "administration_status": (
                    "administered"
                ),
                "recording_id": str(
                    uuid4(),
                ),
                "response_id": str(
                    uuid4(),
                ),
                "audio": {
                    "signed_url": (
                        "https://storage.example/"
                        f"{question.question_code}.wav"
                        "?signature=test"
                    ),
                    "expires_at": (
                        "2099-01-01T00:00:00Z"
                    ),
                    "content_type": (
                        "audio/wav"
                    ),
                    "size_bytes": 3,
                },
                "stt": {
                    "status": "success",
                    "raw_transcript": (
                        "민수 자전거 공원 11시 야구"
                    ),
                },
                "timing": {
                    "prompt_end_to_recording_start_ms": 100,
                    "recording_duration_ms": 1000,
                },
            },
        )

    return {
        "analysis_id": str(
            analysis_id,
        ),
        "assessment_id": str(
            assessment_id,
        ),
        "question_set_version": "cist-v1",
        "wrong_event_rule_version": (
            "wrong-event-v1"
        ),
        "assessment_local_date": (
            "2026-09-05"
        ),
        "timezone": "Asia/Seoul",
        "stt_config": {
            "provider": "google",
            "api_version": "v2",
            "location": "us",
            "model": "chirp_3",
            "language": "ko-KR",
            "automatic_punctuation": True,
        },
        "recognition_plan": {
            "source_question_code": (
                "memory_delayed_free_recall"
            ),
            "recalled_units": {
                "person": True,
                "transport": True,
                "place": True,
                "time": True,
                "activity": True,
            },
            "selected_question_codes": [],
        },
        "responses": responses,
    }


def headers(
    idempotency_key: str = (
        IDEMPOTENCY_KEY
    ),
) -> dict[str, str]:
    return {
        "Authorization": (
            f"Bearer {SERVICE_TOKEN}"
        ),
        "Idempotency-Key": (
            idempotency_key
        ),
    }


def test_prefetch_accepts_one_cist_clip_and_validates_variant(tmp_path: Path) -> None:
    client, _, _, contracts = create_test_client(tmp_path)
    payload = create_request_payload(contracts)
    response_input = next(
        item for item in payload["responses"]
        if item["question_code"] == "orientation_year"
    )
    body = {"question_set_version": "cist-v1", "response": response_input}

    accepted = client.post("/v1/cist-clips/prefetch", headers=headers(), json=body)
    assert accepted.status_code == 202
    assert accepted.json() == {
        "recording_id": response_input["recording_id"], "status": "accepted",
    }

    invalid = client.post(
        "/v1/cist-clips/prefetch", headers=headers(),
        json={**body, "response": {**response_input, "variant_id": "wrong"}},
    )
    assert invalid.status_code == 422


def test_creates_pending_analysis(
    tmp_path: Path,
) -> None:
    (
        client,
        worker,
        repository,
        contracts,
    ) = create_test_client(tmp_path)
    payload = create_request_payload(
        contracts,
    )

    response = client.post(
        "/v1/analyses",
        headers=headers(),
        json=payload,
    )

    assert response.status_code == 202
    assert response.json()["status"] == (
        "pending"
    )
    assert response.json()["analysis_id"] == (
        payload["analysis_id"]
    )
    assert response.headers["location"] == (
        "/v1/analyses/"
        f"{payload['analysis_id']}"
    )
    assert response.headers[
        "retry-after"
    ] == "2"

    analysis_id = UUID(
        payload["analysis_id"],
    )
    stored = repository.get(
        analysis_id,
    )

    assert stored is not None
    assert stored.status.value == "pending"
    assert worker.enqueued_ids == [
        analysis_id,
    ]


def test_gets_pending_analysis(
    tmp_path: Path,
) -> None:
    (
        client,
        _worker,
        _repository,
        contracts,
    ) = create_test_client(tmp_path)
    payload = create_request_payload(
        contracts,
    )

    created = client.post(
        "/v1/analyses",
        headers=headers(),
        json=payload,
    )
    response = client.get(
        (
            "/v1/analyses/"
            f"{payload['analysis_id']}"
        ),
        headers={
            "Authorization": (
                f"Bearer {SERVICE_TOKEN}"
            ),
        },
    )

    assert created.status_code == 202
    assert response.status_code == 200
    assert response.json()["status"] == (
        "pending"
    )
    assert response.json()["result"] is None
    assert response.json()[
        "retry_items"
    ] == []
    assert response.headers[
        "retry-after"
    ] == "2"


def test_replays_identical_create_request(
    tmp_path: Path,
) -> None:
    (
        client,
        worker,
        _repository,
        contracts,
    ) = create_test_client(tmp_path)
    payload = create_request_payload(
        contracts,
    )

    first = client.post(
        "/v1/analyses",
        headers=headers(),
        json=payload,
    )
    second = client.post(
        "/v1/analyses",
        headers=headers(),
        json=payload,
    )

    assert first.status_code == 202
    assert second.status_code == 202
    assert first.json() == second.json()
    assert len(worker.enqueued_ids) == 1


def test_rejects_same_key_with_changed_body(
    tmp_path: Path,
) -> None:
    (
        client,
        _worker,
        _repository,
        contracts,
    ) = create_test_client(tmp_path)
    payload = create_request_payload(
        contracts,
    )

    first = client.post(
        "/v1/analyses",
        headers=headers(),
        json=payload,
    )

    changed = dict(payload)
    changed[
        "assessment_local_date"
    ] = "2026-09-04"

    second = client.post(
        "/v1/analyses",
        headers=headers(),
        json=changed,
    )

    assert first.status_code == 202
    assert second.status_code == 409
    assert second.json()["error"]["code"] == (
        "IDEMPOTENCY_CONFLICT"
    )


def test_rejects_existing_analysis_with_new_key(
    tmp_path: Path,
) -> None:
    (
        client,
        worker,
        _repository,
        contracts,
    ) = create_test_client(tmp_path)
    payload = create_request_payload(
        contracts,
    )

    first = client.post(
        "/v1/analyses",
        headers=headers(),
        json=payload,
    )
    second = client.post(
        "/v1/analyses",
        headers=headers(
            "analysis-create-key-0002",
        ),
        json=payload,
    )

    assert first.status_code == 202
    assert second.status_code == 409
    assert second.json()["error"]["code"] == (
        "INVALID_ANALYSIS_STATE"
    )
    assert len(worker.enqueued_ids) == 1


def test_gets_needs_retry_analysis(
    tmp_path: Path,
) -> None:
    (
        client,
        _worker,
        repository,
        contracts,
    ) = create_test_client(tmp_path)
    payload = create_request_payload(
        contracts,
    )
    analysis_id = UUID(
        payload["analysis_id"],
    )

    created = client.post(
        "/v1/analyses",
        headers=headers(),
        json=payload,
    )

    repository.mark_processing(
        analysis_id,
    )
    repository.mark_needs_retry(
        analysis_id=analysis_id,
        reason_code=(
            "AUDIO_URL_EXPIRED"
        ),
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        ),
    )

    response = client.get(
        f"/v1/analyses/{analysis_id}",
        headers={
            "Authorization": (
                f"Bearer {SERVICE_TOKEN}"
            ),
        },
    )

    assert created.status_code == 202
    assert response.status_code == 200
    assert response.json()["status"] == (
        "needs_retry"
    )
    assert response.json()["retryable"] is True
    assert response.json()["reason_code"] == (
        "AUDIO_URL_EXPIRED"
    )
    assert len(
        response.json()["retry_items"],
    ) == 1
    assert "retry-after" not in (
        response.headers
    )


def test_unknown_analysis_returns_404(
    tmp_path: Path,
) -> None:
    (
        client,
        _worker,
        _repository,
        _contracts,
    ) = create_test_client(tmp_path)

    response = client.get(
        f"/v1/analyses/{uuid4()}",
        headers={
            "Authorization": (
                f"Bearer {SERVICE_TOKEN}"
            ),
        },
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == (
        "ANALYSIS_NOT_FOUND"
    )


def test_gets_completed_legacy_analysis_without_feature_snapshot(
    tmp_path: Path,
) -> None:
    client, _worker, repository, _contracts = create_test_client(tmp_path)
    analysis_id = uuid4()
    assessment_id = uuid4()
    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=assessment_id,
        request_body={"analysis_id": str(analysis_id)},
    )
    repository.mark_processing(analysis_id)
    repository.mark_completed(
        analysis_id=analysis_id,
        result_body={
            "question_set_version": "cist-v1",
            "wrong_event_rule_version": "wrong-event-v1",
            "model_version": "final_fusion_lr_21subjects_ast20_mean_logit_3seed_v2",
            "model_score": 0.42,
            "decision_threshold": 0.38592870327757767,
            "review_threshold": 0.8061380697921943,
            "threshold_version": "fusion-threshold-v2",
            "risk_flag": True,
            "risk_level": "monitoring_needed",
            "features": {
                "ast_logit": 0.1,
                "kcelectra_logit": 0.2,
                "category_balanced_wrong_event_score": 0.3,
                "category_balanced_median_delay": 0.4,
            },
            "question_results": [
                {
                    "question_code": code,
                    "administration_status": "not_applicable",
                    "recording_id": None,
                    "response_id": None,
                    "vad_status": None,
                    "scoring_status": None,
                    "answer_status": None,
                    "wrong_event": None,
                    "wrong_event_reason": None,
                    "response_delay_ms": None,
                    "recognized_memory_units": None,
                }
                for code in sorted(EXPECTED_QUESTION_CODES)
            ],
        },
    )

    response = client.get(
        f"/v1/analyses/{analysis_id}",
        headers={"Authorization": f"Bearer {SERVICE_TOKEN}"},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "completed"
    assert response.json()["result"]["model_score"] == 0.42
    assert response.json()["result"]["feature_snapshot"] is None


def test_analysis_api_requires_authentication(
    tmp_path: Path,
) -> None:
    (
        client,
        worker,
        _repository,
        contracts,
    ) = create_test_client(tmp_path)
    payload = create_request_payload(
        contracts,
    )

    response = client.post(
        "/v1/analyses",
        headers={
            "Idempotency-Key": (
                IDEMPOTENCY_KEY
            ),
        },
        json=payload,
    )

    assert response.status_code == 401
    assert response.json()["error"]["code"] == (
        "UNAUTHORIZED"
    )
    assert worker.enqueued_ids == []
