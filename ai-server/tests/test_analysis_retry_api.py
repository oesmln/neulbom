from copy import deepcopy
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
    AnalysisStatus,
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
CREATE_KEY = "analysis-create-key-retry-test"
RETRY_KEY = "analysis-retry-key-0001"


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
    idempotency_service = IdempotencyService(
        SQLiteIdempotencyRepository(
            tmp_path / "idempotency.sqlite3",
        ),
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


def test_retries_existing_analysis(
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
    analysis_id = prepare_needs_retry(
        client=client,
        repository=repository,
        payload=payload,
    )
    retry_payload = create_retry_payload(
        payload,
    )

    response = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(RETRY_KEY),
        json=retry_payload,
    )

    assert response.status_code == 202
    assert response.json()["analysis_id"] == (
        str(analysis_id)
    )
    assert response.json()["status"] == (
        "pending"
    )
    assert response.headers["location"] == (
        f"/v1/analyses/{analysis_id}"
    )
    assert response.headers[
        "retry-after"
    ] == "2"

    stored = repository.get(
        analysis_id,
    )

    assert stored is not None
    assert stored.status == (
        AnalysisStatus.PENDING
    )
    assert stored.retryable is False
    assert stored.reason_code is None
    assert stored.retry_items == ()

    updated_response = next(
        item
        for item
        in stored.request_body["responses"]
        if item["question_code"]
        == "orientation_year"
    )

    assert updated_response["audio"][
        "signed_url"
    ].startswith(
        "https://storage.example/new-year.wav",
    )
    assert worker.enqueued_ids == [
        analysis_id,
        analysis_id,
    ]

    history = (
        repository.list_archived_attempts(
            analysis_id,
        )
    )

    assert len(history) == 1
    assert history[0].status == (
        AnalysisStatus.NEEDS_RETRY
    )


def test_replays_identical_retry_request(
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
    analysis_id = prepare_needs_retry(
        client=client,
        repository=repository,
        payload=payload,
    )
    retry_payload = create_retry_payload(
        payload,
    )

    first = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(RETRY_KEY),
        json=retry_payload,
    )
    second = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(RETRY_KEY),
        json=retry_payload,
    )

    assert first.status_code == 202
    assert second.status_code == 202
    assert first.json() == second.json()
    assert worker.enqueued_ids == [
        analysis_id,
        analysis_id,
    ]
    assert len(
        repository.list_archived_attempts(
            analysis_id,
        ),
    ) == 1


def test_rejects_changed_body_with_same_key(
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
    analysis_id = prepare_needs_retry(
        client=client,
        repository=repository,
        payload=payload,
    )
    first_payload = create_retry_payload(
        payload,
    )
    changed_payload = deepcopy(
        first_payload,
    )
    changed_payload["items"][0]["audio"][
        "signed_url"
    ] = (
        "https://storage.example/"
        "another-year.wav?signature=test"
    )

    first = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(RETRY_KEY),
        json=first_payload,
    )
    second = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(RETRY_KEY),
        json=changed_payload,
    )

    assert first.status_code == 202
    assert second.status_code == 409
    assert second.json()["error"]["code"] == (
        "IDEMPOTENCY_CONFLICT"
    )
    assert worker.enqueued_ids == [
        analysis_id,
        analysis_id,
    ]
    assert len(
        repository.list_archived_attempts(
            analysis_id,
        ),
    ) == 1


def test_rejects_retry_from_pending_state(
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
    analysis_id = prepare_needs_retry(
        client=client,
        repository=repository,
        payload=payload,
    )
    retry_payload = create_retry_payload(
        payload,
    )

    first = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(RETRY_KEY),
        json=retry_payload,
    )
    second = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(
            "analysis-retry-key-0002",
        ),
        json=retry_payload,
    )

    assert first.status_code == 202
    assert second.status_code == 409
    assert second.json()["error"]["code"] == (
        "INVALID_ANALYSIS_STATE"
    )


def test_unknown_analysis_returns_404(
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
    analysis_id = uuid4()
    retry_payload = create_retry_payload(
        payload,
    )

    response = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(RETRY_KEY),
        json=retry_payload,
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == (
        "ANALYSIS_NOT_FOUND"
    )


def test_rejects_changed_reissue_identifier(
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
    analysis_id = prepare_needs_retry(
        client=client,
        repository=repository,
        payload=payload,
    )
    retry_payload = create_retry_payload(
        payload,
    )
    retry_payload["items"][0][
        "recording_id"
    ] = str(uuid4())

    response = client.post(
        f"/v1/analyses/{analysis_id}/retry",
        headers=headers(RETRY_KEY),
        json=retry_payload,
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == (
        "VALIDATION_ERROR"
    )

    stored = repository.get(
        analysis_id,
    )

    assert stored is not None
    assert stored.status == (
        AnalysisStatus.NEEDS_RETRY
    )
    assert (
        repository.list_archived_attempts(
            analysis_id,
        )
        == ()
    )


def create_request_payload(
    contracts: ContractBundle,
) -> dict:
    analysis_id = uuid4()
    assessment_id = uuid4()
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
                    "size_bytes": 1024,
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
        "analysis_id": str(analysis_id),
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


def prepare_needs_retry(
    *,
    client: TestClient,
    repository: SQLiteAnalysisRepository,
    payload: dict,
) -> UUID:
    created = client.post(
        "/v1/analyses",
        headers=headers(CREATE_KEY),
        json=payload,
    )

    assert created.status_code == 202

    analysis_id = UUID(
        payload["analysis_id"],
    )

    repository.mark_processing(
        analysis_id,
    )
    repository.mark_needs_retry(
        analysis_id=analysis_id,
        reason_code="AUDIO_URL_EXPIRED",
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

    return analysis_id


def create_retry_payload(
    original_payload: dict,
) -> dict:
    original_response = next(
        response
        for response
        in original_payload["responses"]
        if response["question_code"]
        == "orientation_year"
    )

    return {
        "reason_code": "AUDIO_URL_EXPIRED",
        "items": [
            {
                "question_code": (
                    "orientation_year"
                ),
                "retry_action": (
                    "REISSUE_AUDIO_URL"
                ),
                "recording_id": (
                    original_response[
                        "recording_id"
                    ]
                ),
                "response_id": (
                    original_response[
                        "response_id"
                    ]
                ),
                "audio": {
                    "signed_url": (
                        "https://storage.example/"
                        "new-year.wav?signature=test"
                    ),
                    "expires_at": (
                        "2099-01-02T00:00:00Z"
                    ),
                    "content_type": (
                        "audio/wav"
                    ),
                    "size_bytes": 1024,
                },
            },
        ],
    }


def headers(
    idempotency_key: str,
) -> dict[str, str]:
    return {
        "Authorization": (
            f"Bearer {SERVICE_TOKEN}"
        ),
        "Idempotency-Key": (
            idempotency_key
        ),
    }