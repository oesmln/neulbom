from pathlib import Path
from uuid import UUID

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.errors import register_exception_handlers
from app.api.routes.recognition_plan import (
    get_idempotency_service,
    get_recognition_plan_workflow,
    router,
)
from app.api.schemas.common import (
    MemoryUnitMap,
    QuestionAnalysisResult,
)
from app.api.schemas.recognition_plan import (
    RecognitionPlanCompletedResponse,
    RecognitionPlanRequest,
)
from app.core.config import Settings, get_settings
from app.repositories.idempotency import (
    SQLiteIdempotencyRepository,
)
from app.services.idempotency import (
    IdempotencyService,
)

ASSESSMENT_ID = UUID(
    "550e8400-e29b-41d4-a716-446655440000",
)
RECORDING_ID = UUID(
    "0f47ac10-b72d-4f1e-9c0d-a1300939a641",
)
RESPONSE_ID = UUID(
    "5d2cab69-e905-4f7b-8612-c71caa05b7f2",
)
SERVICE_TOKEN = (
    "test-service-token-with-at-least-32-characters"
)
IDEMPOTENCY_KEY = "recognition-plan-key-0001"


class FakeRecognitionPlanWorkflow:
    def __init__(self) -> None:
        self.execution_count = 0

    async def execute(
        self,
        *,
        assessment_id: UUID,
        request: RecognitionPlanRequest,
    ) -> RecognitionPlanCompletedResponse:
        self.execution_count += 1

        recalled_units = MemoryUnitMap(
            person=True,
            transport=False,
            place=True,
            time=False,
            activity=True,
        )

        return RecognitionPlanCompletedResponse(
            assessment_id=assessment_id,
            status="completed",
            question_set_version=(
                request.question_set_version
            ),
            wrong_event_rule_version=(
                request.wrong_event_rule_version
            ),
            recalled_units=recalled_units,
            next_question_codes=[
                "memory_recognition_transport",
                "memory_recognition_time",
            ],
            q11_result=QuestionAnalysisResult(
                question_code=(
                    "memory_delayed_free_recall"
                ),
                administration_status="administered",
                recording_id=(
                    request.response.recording_id
                ),
                response_id=(
                    request.response.response_id
                ),
                vad_status="speech_detected",
                scoring_status="not_scored",
                answer_status=None,
                wrong_event=0,
                wrong_event_reason=None,
                response_delay_ms=640,
                recognized_memory_units=(
                    recalled_units
                ),
            ),
        )


def test_creates_recognition_plan(
    tmp_path: Path,
) -> None:
    workflow = FakeRecognitionPlanWorkflow()
    client = _create_client(
        tmp_path=tmp_path,
        workflow=workflow,
    )

    response = client.post(
        (
            f"/v1/assessments/{ASSESSMENT_ID}"
            "/recognition-plan"
        ),
        headers=_headers(),
        json=_request_payload(),
    )

    assert response.status_code == 200
    assert response.json()["status"] == "completed"
    assert response.json()[
        "next_question_codes"
    ] == [
        "memory_recognition_transport",
        "memory_recognition_time",
    ]
    assert workflow.execution_count == 1


def test_accepts_webm_audio_resource(
    tmp_path: Path,
) -> None:
    workflow = FakeRecognitionPlanWorkflow()
    client = _create_client(
        tmp_path=tmp_path,
        workflow=workflow,
    )
    payload = _request_payload()
    payload["response"]["audio"] = {
        "signed_url": (
            "https://storage.example/q11.webm"
        ),
        "expires_at": "2099-09-01T10:30:00Z",
        "content_type": "audio/webm",
        "size_bytes": 123456,
    }

    response = client.post(
        (
            f"/v1/assessments/{ASSESSMENT_ID}"
            "/recognition-plan"
        ),
        headers={
            "Authorization": (
                f"Bearer {SERVICE_TOKEN}"
            ),
            "Idempotency-Key": (
                "recognition-plan-webm-0001"
            ),
        },
        json=payload,
    )

    assert response.status_code == 200
    assert workflow.execution_count == 1


def test_replays_identical_request(
    tmp_path: Path,
) -> None:
    workflow = FakeRecognitionPlanWorkflow()
    client = _create_client(
        tmp_path=tmp_path,
        workflow=workflow,
    )
    path = (
        f"/v1/assessments/{ASSESSMENT_ID}"
        "/recognition-plan"
    )

    first = client.post(
        path,
        headers=_headers(),
        json=_request_payload(),
    )
    second = client.post(
        path,
        headers=_headers(),
        json=_request_payload(),
    )

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json() == second.json()
    assert workflow.execution_count == 1


def test_rejects_same_key_with_different_body(
    tmp_path: Path,
) -> None:
    workflow = FakeRecognitionPlanWorkflow()
    client = _create_client(
        tmp_path=tmp_path,
        workflow=workflow,
    )
    path = (
        f"/v1/assessments/{ASSESSMENT_ID}"
        "/recognition-plan"
    )

    first = client.post(
        path,
        headers=_headers(),
        json=_request_payload(),
    )

    changed_payload = _request_payload()
    changed_payload["response"]["stt"][
        "raw_transcript"
    ] = "민수와 자전거"

    second = client.post(
        path,
        headers=_headers(),
        json=changed_payload,
    )

    assert first.status_code == 200
    assert second.status_code == 409
    assert second.json()["error"]["code"] == (
        "IDEMPOTENCY_CONFLICT"
    )
    assert workflow.execution_count == 1


def test_requires_bearer_token(
    tmp_path: Path,
) -> None:
    workflow = FakeRecognitionPlanWorkflow()
    client = _create_client(
        tmp_path=tmp_path,
        workflow=workflow,
    )

    response = client.post(
        (
            f"/v1/assessments/{ASSESSMENT_ID}"
            "/recognition-plan"
        ),
        headers={
            "Idempotency-Key": IDEMPOTENCY_KEY,
        },
        json=_request_payload(),
    )

    assert response.status_code == 401
    assert response.json()["error"]["code"] == (
        "UNAUTHORIZED"
    )
    assert workflow.execution_count == 0


def _create_client(
    *,
    tmp_path: Path,
    workflow: FakeRecognitionPlanWorkflow,
) -> TestClient:
    application = FastAPI()
    register_exception_handlers(application)
    application.include_router(router)

    settings = Settings(
        _env_file=None,
        service_token=SERVICE_TOKEN,
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
        get_recognition_plan_workflow
    ] = lambda: workflow
    application.dependency_overrides[
        get_idempotency_service
    ] = lambda: idempotency_service

    return TestClient(application)


def _headers() -> dict[str, str]:
    return {
        "Authorization": (
            f"Bearer {SERVICE_TOKEN}"
        ),
        "Idempotency-Key": IDEMPOTENCY_KEY,
    }


def _request_payload() -> dict:
    return {
        "question_set_version": "cist-v1",
        "wrong_event_rule_version": (
            "wrong-event-v1"
        ),
        "assessment_local_date": "2026-09-01",
        "timezone": "Asia/Seoul",
        "stt_config": {
            "provider": "google",
            "api_version": "v2",
            "location": "us",
            "model": "chirp_3",
            "language": "ko-KR",
            "automatic_punctuation": True,
        },
        "response": {
            "question_code": (
                "memory_delayed_free_recall"
            ),
            "variant_id": (
                "memory-delayed-free-recall-fixed-v1"
            ),
            "administration_status": "administered",
            "recording_id": str(RECORDING_ID),
            "response_id": str(RESPONSE_ID),
            "audio": {
                "signed_url": (
                    "https://storage.example/"
                    "q11.m4a?signature=test"
                ),
                "expires_at": (
                    "2099-09-01T10:30:00Z"
                ),
                "content_type": "audio/mp4",
                "size_bytes": 123456,
            },
            "stt": {
                "status": "success",
                "raw_transcript": (
                    "민수는 공원에 가서 야구를 했어요"
                ),
            },
            "timing": {
                "prompt_end_to_recording_start_ms": 180,
                "recording_duration_ms": 4250,
            },
        },
    }
