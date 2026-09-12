from copy import deepcopy
from datetime import UTC, datetime
from typing import get_args
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.api.schemas.analysis import (
    AnalysisStatusResponse,
    FinalAnalysisResult,
)
from app.api.schemas.common import (
    ConditionalQuestionCode,
    QuestionAnalysisResult,
    QuestionCode,
)

CONDITIONAL_CODES = set(
    get_args(ConditionalQuestionCode),
)

UNSCORED_CODES = {
    "orientation_place",
    "memory_registration_first",
    "memory_registration_second",
    "memory_delayed_free_recall",
    "language_semantic_fluency",
}


def question_results() -> list[dict]:
    results: list[dict] = []

    for question_code in get_args(
        QuestionCode,
    ):
        if question_code in CONDITIONAL_CODES:
            results.append(
                {
                    "question_code": question_code,
                    "administration_status": (
                        "not_applicable"
                    ),
                    "recording_id": None,
                    "response_id": None,
                    "vad_status": None,
                    "scoring_status": None,
                    "answer_status": None,
                    "wrong_event": None,
                    "wrong_event_reason": None,
                    "response_delay_ms": None,
                    "recognized_memory_units": None,
                },
            )
            continue

        not_scored = (
            question_code in UNSCORED_CODES
        )

        results.append(
            {
                "question_code": question_code,
                "administration_status": (
                    "administered"
                ),
                "recording_id": str(
                    uuid4(),
                ),
                "response_id": str(
                    uuid4(),
                ),
                "vad_status": (
                    "speech_detected"
                ),
                "scoring_status": (
                    "not_scored"
                    if not_scored
                    else "scored"
                ),
                "answer_status": (
                    None
                    if not_scored
                    else "correct"
                ),
                "wrong_event": (
                    None
                    if question_code in {
                        "orientation_place",
                        "language_semantic_fluency",
                    }
                    else 0
                ),
                "wrong_event_reason": None,
                "response_delay_ms": 500,
                "recognized_memory_units": None,
            },
        )

    return results


def final_result_payload() -> dict:
    return {
        "question_set_version": "cist-v1",
        "wrong_event_rule_version": (
            "wrong-event-v1"
        ),
        "model_version": (
            "final_fusion_lr_"
            "21subjects_ast20_mean_logit_3seed_v2"
        ),
        "model_score": 0.75,
        "decision_threshold": (
            0.38592870327757767
        ),
        "review_threshold": 0.8061380697921943,
        "threshold_version": (
            "fusion-threshold-v2"
        ),
        "risk_flag": True,
        "risk_level": "monitoring_needed",
        "features": {
            "ast_logit": -0.25,
            "kcelectra_logit": 0.5,
            "category_balanced_wrong_event_score": (
                0.375
            ),
            "category_balanced_median_delay": (
                0.8
            ),
        },
        "question_results": (
            question_results()
        ),
    }


def base_status_payload() -> dict:
    now = datetime.now(UTC).isoformat()

    return {
        "analysis_id": str(uuid4()),
        "assessment_id": str(uuid4()),
        "status": "pending",
        "created_at": now,
        "updated_at": now,
        "retryable": False,
        "reason_code": None,
        "retry_items": [],
        "result": None,
    }


def test_accepts_valid_final_result() -> None:
    result = FinalAnalysisResult.model_validate(
        final_result_payload(),
    )

    assert result.model_score == 0.75
    assert result.risk_flag is True
    assert len(result.question_results) == 17
    assert (
        result.risk_level
        == "monitoring_needed"
    )


def test_rejects_inconsistent_risk_flag() -> None:
    payload = final_result_payload()
    payload["risk_flag"] = False

    with pytest.raises(
        ValidationError,
        match="risk_flag",
    ):
        FinalAnalysisResult.model_validate(
            payload,
        )


def test_rejects_duplicate_question_result() -> None:
    payload = final_result_payload()
    payload["question_results"][-1] = (
        deepcopy(
            payload["question_results"][0],
        )
    )

    with pytest.raises(
        ValidationError,
        match="중복",
    ):
        FinalAnalysisResult.model_validate(
            payload,
        )


def test_rejects_non_finite_feature() -> None:
    payload = final_result_payload()
    payload["features"]["ast_logit"] = (
        float("inf")
    )

    with pytest.raises(
        ValidationError,
        match="유한",
    ):
        FinalAnalysisResult.model_validate(
            payload,
        )


def test_accepts_pending_status() -> None:
    result = (
        AnalysisStatusResponse.model_validate(
            base_status_payload(),
        )
    )

    assert result.status == "pending"
    assert result.result is None


def test_accepts_completed_status() -> None:
    payload = base_status_payload()
    payload["status"] = "completed"
    payload["result"] = (
        final_result_payload()
    )

    result = (
        AnalysisStatusResponse.model_validate(
            payload,
        )
    )

    assert result.status == "completed"
    assert result.result is not None


def test_completed_requires_result() -> None:
    payload = base_status_payload()
    payload["status"] = "completed"

    with pytest.raises(
        ValidationError,
        match="최종 결과",
    ):
        AnalysisStatusResponse.model_validate(
            payload,
        )


def test_accepts_needs_retry_status() -> None:
    payload = base_status_payload()
    payload.update(
        {
            "status": "needs_retry",
            "retryable": True,
            "reason_code": (
                "AUDIO_URL_EXPIRED"
            ),
            "retry_items": [
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
            ],
        },
    )

    result = (
        AnalysisStatusResponse.model_validate(
            payload,
        )
    )

    assert result.status == "needs_retry"
    assert result.retryable is True


def test_needs_retry_requires_items() -> None:
    payload = base_status_payload()
    payload.update(
        {
            "status": "needs_retry",
            "retryable": True,
            "reason_code": (
                "UNSCORABLE_STT"
            ),
        },
    )

    with pytest.raises(
        ValidationError,
        match="retry_items",
    ):
        AnalysisStatusResponse.model_validate(
            payload,
        )


def test_accepts_failed_status() -> None:
    payload = base_status_payload()
    payload.update(
        {
            "status": "failed",
            "reason_code": (
                "MODEL_UNAVAILABLE"
            ),
        },
    )

    result = (
        AnalysisStatusResponse.model_validate(
            payload,
        )
    )

    assert result.status == "failed"
    assert result.retryable is False


def test_failed_rejects_retry_reason() -> None:
    payload = base_status_payload()
    payload.update(
        {
            "status": "failed",
            "reason_code": (
                "AUDIO_URL_EXPIRED"
            ),
        },
    )

    with pytest.raises(
        ValidationError,
        match="failed",
    ):
        AnalysisStatusResponse.model_validate(
            payload,
        )


def test_not_applicable_fields_must_be_null() -> None:
    payload = {
        "question_code": (
            "memory_recognition_person"
        ),
        "administration_status": (
            "not_applicable"
        ),
        "recording_id": str(uuid4()),
        "response_id": None,
        "vad_status": None,
        "scoring_status": None,
        "answer_status": None,
        "wrong_event": None,
        "wrong_event_reason": None,
        "response_delay_ms": None,
        "recognized_memory_units": None,
    }

    with pytest.raises(
        ValidationError,
        match="모두 null",
    ):
        QuestionAnalysisResult.model_validate(
            payload,
        )


def test_no_response_cannot_have_delay() -> None:
    payload = {
        "question_code": (
            "orientation_place"
        ),
        "administration_status": (
            "administered"
        ),
        "recording_id": str(uuid4()),
        "response_id": str(uuid4()),
        "vad_status": "no_response",
        "scoring_status": "not_scored",
        "answer_status": None,
        "wrong_event": None,
        "wrong_event_reason": None,
        "response_delay_ms": 100,
        "recognized_memory_units": None,
    }

    with pytest.raises(
        ValidationError,
        match="응답 지연",
    ):
        QuestionAnalysisResult.model_validate(
            payload,
        )

def test_rejects_inconsistent_risk_level() -> None:
    payload = final_result_payload()
    payload["risk_level"] = "review_needed"

    with pytest.raises(
        ValidationError,
        match="risk_level",
    ):
        FinalAnalysisResult.model_validate(
            payload,
        )
