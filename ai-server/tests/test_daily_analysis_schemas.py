from copy import deepcopy
from datetime import UTC, datetime
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.api.schemas.analysis import (
    DailyAnalysisCreateRequest,
    DailyAnalysisResult,
    DailyAnalysisStatusResponse,
)
from tests.test_analysis_result_schemas import (
    final_result_payload,
)


def administered_response(
    question_code: str,
) -> dict:
    return {
        "question_code": question_code,
        "variant_id": f"{question_code}-v1",
        "administration_status": "administered",
        "recording_id": str(uuid4()),
        "response_id": str(uuid4()),
        "audio": {
            "signed_url": (
                "https://storage.example/"
                f"{question_code}.wav?signature=test"
            ),
            "expires_at": (
                "2099-01-01T00:00:00Z"
            ),
            "content_type": "audio/wav",
            "size_bytes": 1024,
        },
        "stt": {
            "status": "success",
            "raw_transcript": "테스트 응답",
        },
        "timing": {
            "prompt_end_to_recording_start_ms": 100,
            "recording_duration_ms": 1000,
        },
    }


def daily_create_payload() -> dict:
    baseline = final_result_payload()

    return {
        "analysis_type": (
            "daily_partial_update"
        ),
        "analysis_id": str(uuid4()),
        "session_id": str(uuid4()),
        "baseline_analysis_id": str(
            uuid4(),
        ),
        "question_set_version": "cist-v1",
        "wrong_event_rule_version": (
            "wrong-event-v1"
        ),
        "assessment_local_date": (
            "2026-09-29"
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
        "baseline_model_score": 0.7,
        "input_snapshot": deepcopy(
            baseline["feature_snapshot"],
        ),
        "responses": [
            administered_response(
                "orientation_year",
            ),
            administered_response(
                "attention_digit_span_4",
            ),
        ],
    }


def daily_result_payload() -> dict:
    request = daily_create_payload()
    snapshot = deepcopy(
        request["input_snapshot"],
    )
    snapshot["model_score"] = 0.8

    return {
        "result_type": (
            "daily_partial_estimate"
        ),
        "baseline_analysis_id": (
            request["baseline_analysis_id"]
        ),
        "baseline_model_score": 0.7,
        "input_model_score": 0.75,
        "estimated_model_score": 0.8,
        "score_delta_from_baseline": 0.1,
        "score_delta_from_previous": 0.05,
        "model_version": (
            "final_fusion_lr_21subjects_"
            "ast20_mean_logit_3seed_v2"
        ),
        "decision_threshold": (
            0.38592870327757767
        ),
        "review_threshold": (
            0.8061380697921943
        ),
        "threshold_version": (
            "fusion-threshold-v2"
        ),
        "risk_flag": True,
        "risk_level": "monitoring_needed",
        "updated_question_codes": [
            "orientation_year",
            "attention_digit_span_4",
        ],
        "features": deepcopy(
            snapshot["fusion_features"],
        ),
        "output_snapshot": snapshot,
        "question_results": [
            _question_result(
                "orientation_year",
            ),
            _question_result(
                "attention_digit_span_4",
            ),
        ],
    }


def _question_result(
    question_code: str,
) -> dict:
    return {
        "question_code": question_code,
        "administration_status": (
            "administered"
        ),
        "recording_id": str(uuid4()),
        "response_id": str(uuid4()),
        "vad_status": "speech_detected",
        "scoring_status": "scored",
        "answer_status": "correct",
        "wrong_event": 0,
        "wrong_event_reason": None,
        "response_delay_ms": 500,
        "recognized_memory_units": None,
    }


def test_accepts_daily_partial_update_request(
) -> None:
    request = (
        DailyAnalysisCreateRequest
        .model_validate(
            daily_create_payload(),
        )
    )

    assert {
        response.question_code
        for response in request.responses
    } == {
        "orientation_year",
        "attention_digit_span_4",
    }


def test_rejects_daily_request_without_both_domains(
) -> None:
    payload = daily_create_payload()
    payload["responses"][1] = (
        administered_response(
            "orientation_month",
        )
    )

    with pytest.raises(
        ValidationError,
        match="지남력 1문항과 주의력 1문항",
    ):
        DailyAnalysisCreateRequest.model_validate(
            payload,
        )


def test_rejects_snapshot_contract_mismatch(
) -> None:
    payload = daily_create_payload()
    payload["input_snapshot"][
        "wrong_event_rule_version"
    ] = "wrong-event-v0"

    with pytest.raises(
        ValidationError,
    ):
        DailyAnalysisCreateRequest.model_validate(
            payload,
        )


def test_accepts_consistent_daily_result(
) -> None:
    result = DailyAnalysisResult.model_validate(
        daily_result_payload(),
    )

    assert result.estimated_model_score == 0.8
    assert result.score_delta_from_baseline == (
        pytest.approx(0.1)
    )


@pytest.mark.parametrize(
    ("field", "value", "message"),
    (
        (
            "score_delta_from_baseline",
            0.2,
            "기준 점수 대비 변화량",
        ),
        (
            "score_delta_from_previous",
            0.2,
            "직전 점수 대비 변화량",
        ),
        (
            "risk_flag",
            False,
            "risk_flag",
        ),
        (
            "risk_level",
            "review_needed",
            "risk_level",
        ),
    ),
)
def test_rejects_inconsistent_daily_result(
    field: str,
    value,
    message: str,
) -> None:
    payload = daily_result_payload()
    payload[field] = value

    with pytest.raises(
        ValidationError,
        match=message,
    ):
        DailyAnalysisResult.model_validate(
            payload,
        )


def test_rejects_mismatched_updated_questions(
) -> None:
    payload = daily_result_payload()
    payload["updated_question_codes"][1] = (
        "attention_digit_span_5"
    )

    with pytest.raises(
        ValidationError,
        match="갱신 문항",
    ):
        DailyAnalysisResult.model_validate(
            payload,
        )


def test_accepts_completed_daily_status() -> None:
    now = datetime.now(UTC).isoformat()
    result = (
        DailyAnalysisStatusResponse
        .model_validate(
            {
                "analysis_id": str(uuid4()),
                "session_id": str(uuid4()),
                "status": "completed",
                "created_at": now,
                "updated_at": now,
                "retryable": False,
                "reason_code": None,
                "retry_items": [],
                "result": (
                    daily_result_payload()
                ),
            },
        )
    )

    assert result.result is not None
