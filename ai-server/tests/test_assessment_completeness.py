from copy import deepcopy
from pathlib import Path
from uuid import (
    NAMESPACE_URL,
    uuid4,
    uuid5,
)

import pytest

from app.api.schemas.analysis import (
    AnalysisCreateRequest,
)
from app.contracts.loader import (
    load_contract_bundle,
)
from app.services.assessment_completeness import (
    AssessmentCompletenessError,
    AssessmentCompletenessService,
)

QUESTION_VARIANTS = {
    "orientation_year": (
        "orientation-year-fixed-v1"
    ),
    "orientation_month": (
        "orientation-month-fixed-v1"
    ),
    "orientation_day": (
        "orientation-day-fixed-v1"
    ),
    "orientation_weekday": (
        "orientation-weekday-fixed-v1"
    ),
    "orientation_place": (
        "orientation-place-fixed-v1"
    ),
    "memory_registration_first": (
        "memory-registration-first-fixed-v1"
    ),
    "memory_registration_second": (
        "memory-registration-second-fixed-v1"
    ),
    "attention_digit_span_4": (
        "attention-digit-span-4-fixed-v1"
    ),
    "attention_digit_span_5": (
        "attention-digit-span-5-fixed-v1"
    ),
    "attention_word_reverse": (
        "attention-word-reverse-fixed-v1"
    ),
    "memory_delayed_free_recall": (
        "memory-delayed-free-recall-fixed-v1"
    ),
    "memory_recognition_person": (
        "memory-recognition-person-fixed-v1"
    ),
    "memory_recognition_transport": (
        "memory-recognition-transport-fixed-v1"
    ),
    "memory_recognition_place": (
        "memory-recognition-place-fixed-v1"
    ),
    "memory_recognition_time": (
        "memory-recognition-time-fixed-v1"
    ),
    "memory_recognition_activity": (
        "memory-recognition-activity-fixed-v1"
    ),
    "language_semantic_fluency": (
        "language-semantic-fluency-fixed-v1"
    ),
}

CONDITIONAL_CODES = {
    "memory_recognition_person",
    "memory_recognition_transport",
    "memory_recognition_place",
    "memory_recognition_time",
    "memory_recognition_activity",
}


@pytest.fixture
def service() -> AssessmentCompletenessService:
    project_root = (
        Path(__file__).resolve().parents[1]
    )
    bundle = load_contract_bundle(
        project_root / "contracts",
    )

    return (
        AssessmentCompletenessService
        .from_contract_bundle(bundle)
    )


def test_accepts_complete_assessment(
    service: AssessmentCompletenessService,
) -> None:
    request = AnalysisCreateRequest.model_validate(
        _valid_payload(),
    )

    result = service.validate(request)

    assert len(
        result.administered_responses,
    ) == 12
    assert set(
        result.not_applicable_question_codes,
    ) == CONDITIONAL_CODES
    assert [
        response.question_code
        for response
        in result.administered_responses
    ][:3] == [
        "orientation_year",
        "orientation_month",
        "orientation_day",
    ]


def test_accepts_selected_conditional_question(
    service: AssessmentCompletenessService,
) -> None:
    payload = _valid_payload()
    payload["recognition_plan"][
        "recalled_units"
    ]["person"] = False
    payload["recognition_plan"][
        "selected_question_codes"
    ] = [
        "memory_recognition_person",
    ]
    _replace_response(
        payload,
        "memory_recognition_person",
        _administered_response(
            "memory_recognition_person",
        ),
    )

    request = AnalysisCreateRequest.model_validate(
        payload,
    )
    result = service.validate(request)

    assert len(
        result.administered_responses,
    ) == 13
    assert (
        "memory_recognition_person"
        not in result
        .not_applicable_question_codes
    )


def test_rejects_duplicate_question_code(
    service: AssessmentCompletenessService,
) -> None:
    payload = _valid_payload()
    payload["responses"][-1] = deepcopy(
        payload["responses"][0],
    )
    request = AnalysisCreateRequest.model_validate(
        payload,
    )

    with pytest.raises(
        AssessmentCompletenessError,
        match="중복",
    ):
        service.validate(request)


def test_rejects_wrong_variant(
    service: AssessmentCompletenessService,
) -> None:
    payload = _valid_payload()
    payload["responses"][0][
        "variant_id"
    ] = "wrong-variant"
    request = AnalysisCreateRequest.model_validate(
        payload,
    )

    with pytest.raises(
        AssessmentCompletenessError,
        match="variant_id",
    ):
        service.validate(request)


def test_rejects_plan_not_matching_recalled_units(
    service: AssessmentCompletenessService,
) -> None:
    payload = _valid_payload()
    payload["recognition_plan"][
        "recalled_units"
    ]["person"] = False
    request = AnalysisCreateRequest.model_validate(
        payload,
    )

    with pytest.raises(
        AssessmentCompletenessError,
        match="recalled_units",
    ):
        service.validate(request)


def test_rejects_selected_but_not_applicable(
    service: AssessmentCompletenessService,
) -> None:
    payload = _valid_payload()
    payload["recognition_plan"][
        "recalled_units"
    ]["person"] = False
    payload["recognition_plan"][
        "selected_question_codes"
    ] = [
        "memory_recognition_person",
    ]
    request = AnalysisCreateRequest.model_validate(
        payload,
    )

    with pytest.raises(
        AssessmentCompletenessError,
        match="시행 상태",
    ):
        service.validate(request)


def test_rejects_unselected_but_administered(
    service: AssessmentCompletenessService,
) -> None:
    payload = _valid_payload()
    _replace_response(
        payload,
        "memory_recognition_person",
        _administered_response(
            "memory_recognition_person",
        ),
    )
    request = AnalysisCreateRequest.model_validate(
        payload,
    )

    with pytest.raises(
        AssessmentCompletenessError,
        match="시행 상태",
    ):
        service.validate(request)


def test_rejects_duplicate_recording_id(
    service: AssessmentCompletenessService,
) -> None:
    payload = _valid_payload()
    first_id = payload["responses"][0][
        "recording_id"
    ]
    payload["responses"][1][
        "recording_id"
    ] = first_id
    request = AnalysisCreateRequest.model_validate(
        payload,
    )

    with pytest.raises(
        AssessmentCompletenessError,
        match="recording_id",
    ):
        service.validate(request)


def _valid_payload() -> dict:
    responses = []

    for question_code in QUESTION_VARIANTS:
        if question_code in CONDITIONAL_CODES:
            responses.append(
                {
                    "question_code": question_code,
                    "variant_id": (
                        QUESTION_VARIANTS[
                            question_code
                        ]
                    ),
                    "administration_status": (
                        "not_applicable"
                    ),
                },
            )
        else:
            responses.append(
                _administered_response(
                    question_code,
                ),
            )

    return {
        "analysis_id": str(uuid4()),
        "assessment_id": str(uuid4()),
        "question_set_version": "cist-v1",
        "wrong_event_rule_version": (
            "wrong-event-v1"
        ),
        "assessment_local_date": "2026-09-04",
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


def _administered_response(
    question_code: str,
) -> dict:
    recording_id = uuid5(
        NAMESPACE_URL,
        f"recording:{question_code}",
    )
    response_id = uuid5(
        NAMESPACE_URL,
        f"response:{question_code}",
    )

    return {
        "question_code": question_code,
        "variant_id": (
            QUESTION_VARIANTS[
                question_code
            ]
        ),
        "administration_status": "administered",
        "recording_id": str(recording_id),
        "response_id": str(response_id),
        "audio": {
            "signed_url": (
                "https://storage.example/"
                f"{question_code}.wav?signature=test"
            ),
            "expires_at": (
                "2099-09-04T10:30:00Z"
            ),
            "content_type": "audio/wav",
            "size_bytes": 1024,
        },
        "stt": {
            "status": "success",
            "raw_transcript": "테스트 답변",
        },
        "timing": {
            "prompt_end_to_recording_start_ms": 100,
            "recording_duration_ms": 1000,
        },
    }


def _replace_response(
    payload: dict,
    question_code: str,
    replacement: dict,
) -> None:
    for index, response in enumerate(
        payload["responses"],
    ):
        if (
            response["question_code"]
            == question_code
        ):
            payload["responses"][
                index
            ] = replacement
            return

    raise AssertionError(
        f"문항을 찾을 수 없습니다: {question_code}",
    )