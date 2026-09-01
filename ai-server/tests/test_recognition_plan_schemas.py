from copy import deepcopy
from uuid import UUID

import pytest
from pydantic import ValidationError

from app.api.schemas.recognition_plan import (
    RecognitionPlanCompletedResponse,
    RecognitionPlanRequest,
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


def test_accepts_valid_recognition_plan_request() -> None:
    request = RecognitionPlanRequest.model_validate(
        _valid_request_payload(),
    )

    assert request.question_set_version == "cist-v1"
    assert request.timezone == "Asia/Seoul"
    assert (
        request.response.question_code
        == "memory_delayed_free_recall"
    )


def test_rejects_wrong_q11_question_code() -> None:
    payload = _valid_request_payload()
    payload["response"]["question_code"] = (
        "memory_registration_first"
    )

    with pytest.raises(ValidationError):
        RecognitionPlanRequest.model_validate(payload)


def test_rejects_unknown_request_field() -> None:
    payload = _valid_request_payload()
    payload["unexpected_field"] = True

    with pytest.raises(ValidationError):
        RecognitionPlanRequest.model_validate(payload)


@pytest.mark.parametrize(
    ("status", "transcript"),
    [
        ("success", None),
        ("success", "   "),
        ("empty_transcript", "민수"),
        ("failed", ""),
        ("failed", "민수"),
    ],
)
def test_rejects_inconsistent_stt_status(
    status: str,
    transcript: str | None,
) -> None:
    payload = _valid_request_payload()
    payload["response"]["stt"] = {
        "status": status,
        "raw_transcript": transcript,
    }

    with pytest.raises(ValidationError):
        RecognitionPlanRequest.model_validate(payload)


def test_rejects_non_https_audio_url() -> None:
    payload = _valid_request_payload()
    payload["response"]["audio"]["signed_url"] = (
        "http://storage.example/q11.m4a"
    )

    with pytest.raises(ValidationError):
        RecognitionPlanRequest.model_validate(payload)


def test_accepts_completed_response_with_matching_plan() -> None:
    response = RecognitionPlanCompletedResponse.model_validate(
        _valid_completed_response_payload(),
    )

    assert response.status == "completed"
    assert response.next_question_codes == [
        "memory_recognition_transport",
        "memory_recognition_time",
    ]


def test_rejects_completed_response_with_wrong_plan() -> None:
    payload = _valid_completed_response_payload()
    payload["next_question_codes"] = [
        "memory_recognition_person",
    ]

    with pytest.raises(
        ValidationError,
        match="recalled_units",
    ):
        RecognitionPlanCompletedResponse.model_validate(
            payload,
        )


def test_rejects_mismatched_q11_memory_units() -> None:
    payload = _valid_completed_response_payload()
    payload["q11_result"][
        "recognized_memory_units"
    ]["person"] = False

    with pytest.raises(
        ValidationError,
        match="recognized_memory_units",
    ):
        RecognitionPlanCompletedResponse.model_validate(
            payload,
        )


def _valid_request_payload() -> dict:
    return {
        "question_set_version": "cist-v1",
        "wrong_event_rule_version": "wrong-event-v1",
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
                    "2026-09-01T10:30:00Z"
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


def _valid_completed_response_payload() -> dict:
    recalled_units = {
        "person": True,
        "transport": False,
        "place": True,
        "time": False,
        "activity": True,
    }

    return {
        "assessment_id": str(ASSESSMENT_ID),
        "status": "completed",
        "question_set_version": "cist-v1",
        "wrong_event_rule_version": "wrong-event-v1",
        "recalled_units": deepcopy(recalled_units),
        "next_question_codes": [
            "memory_recognition_transport",
            "memory_recognition_time",
        ],
        "q11_result": {
            "question_code": (
                "memory_delayed_free_recall"
            ),
            "administration_status": "administered",
            "recording_id": str(RECORDING_ID),
            "response_id": str(RESPONSE_ID),
            "vad_status": "speech_detected",
            "scoring_status": "not_scored",
            "answer_status": None,
            "wrong_event": 0,
            "wrong_event_reason": None,
            "response_delay_ms": 640,
            "recognized_memory_units": deepcopy(
                recalled_units,
            ),
        },
    }