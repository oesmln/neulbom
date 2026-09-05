from datetime import date
from pathlib import Path

import pytest

from app.contracts.loader import (
    load_contract_bundle,
)
from app.scoring.objective import (
    ObjectiveScoringService,
)

ASSESSMENT_DATE = date(
    2026,
    9,
    3,
)


@pytest.fixture
def service() -> ObjectiveScoringService:
    project_root = (
        Path(__file__).resolve().parents[1]
    )
    bundle = load_contract_bundle(
        project_root / "contracts",
    )

    return (
        ObjectiveScoringService
        .from_contract_bundle(bundle)
    )


@pytest.mark.parametrize(
    ("question_code", "transcript"),
    [
        (
            "orientation_year",
            "이천이십육 년입니다.",
        ),
        (
            "orientation_month",
            "구 월이에요.",
        ),
        (
            "orientation_day",
            "삼 일입니다.",
        ),
        (
            "orientation_weekday",
            "목요일입니다.",
        ),
    ],
)
def test_scores_correct_orientation_answer(
    service: ObjectiveScoringService,
    question_code: str,
    transcript: str,
) -> None:
    result = service.score(
        question_code=question_code,
        raw_transcript=transcript,
        assessment_local_date=ASSESSMENT_DATE,
    )

    assert result.answer_status == "correct"
    assert result.wrong_event == 0
    assert result.wrong_event_reason is None


@pytest.mark.parametrize(
    ("question_code", "transcript"),
    [
        (
            "orientation_year",
            "2025년",
        ),
        (
            "orientation_month",
            "8월",
        ),
        (
            "orientation_day",
            "4일",
        ),
        (
            "orientation_weekday",
            "금요일",
        ),
    ],
)
def test_scores_incorrect_orientation_answer(
    service: ObjectiveScoringService,
    question_code: str,
    transcript: str,
) -> None:
    result = service.score(
        question_code=question_code,
        raw_transcript=transcript,
        assessment_local_date=ASSESSMENT_DATE,
    )

    assert result.answer_status == "incorrect"
    assert result.wrong_event == 1
    assert (
        result.wrong_event_reason
        == "OBJECTIVE_INCORRECT"
    )


@pytest.mark.parametrize(
    ("question_code", "transcript"),
    [
        (
            "attention_digit_span_4",
            "6 9 7 3",
        ),
        (
            "attention_digit_span_4",
            "육 구 칠 삼",
        ),
        (
            "attention_digit_span_5",
            "5 7 2 8 4",
        ),
        (
            "attention_word_reverse",
            "산 강 수 금",
        ),
    ],
)
def test_scores_correct_exact_sequence(
    service: ObjectiveScoringService,
    question_code: str,
    transcript: str,
) -> None:
    result = service.score(
        question_code=question_code,
        raw_transcript=transcript,
        assessment_local_date=ASSESSMENT_DATE,
    )

    assert result.answer_status == "correct"
    assert result.wrong_event == 0


@pytest.mark.parametrize(
    ("question_code", "transcript"),
    [
        (
            "attention_digit_span_4",
            "6 9 3 7",
        ),
        (
            "attention_digit_span_4",
            "6 9 7 3 1",
        ),
        (
            "attention_digit_span_5",
            "5 7 2 4 8",
        ),
        (
            "attention_word_reverse",
            "산 강 수 금 나무",
        ),
    ],
)
def test_rejects_wrong_order_or_extra_units(
    service: ObjectiveScoringService,
    question_code: str,
    transcript: str,
) -> None:
    result = service.score(
        question_code=question_code,
        raw_transcript=transcript,
        assessment_local_date=ASSESSMENT_DATE,
    )

    assert result.answer_status == "incorrect"
    assert result.wrong_event == 1


@pytest.mark.parametrize(
    ("question_code", "transcript"),
    [
        (
            "memory_recognition_person",
            "민수입니다.",
        ),
        (
            "memory_recognition_transport",
            "자전거요.",
        ),
        (
            "memory_recognition_place",
            "공원",
        ),
        (
            "memory_recognition_time",
            "열한 시입니다.",
        ),
        (
            "memory_recognition_activity",
            "야구요.",
        ),
    ],
)
def test_scores_correct_recognition_answer(
    service: ObjectiveScoringService,
    question_code: str,
    transcript: str,
) -> None:
    result = service.score(
        question_code=question_code,
        raw_transcript=transcript,
        assessment_local_date=ASSESSMENT_DATE,
    )

    assert result.answer_status == "correct"
    assert result.wrong_event == 0


@pytest.mark.parametrize(
    ("question_code", "transcript"),
    [
        (
            "memory_recognition_person",
            "영수",
        ),
        (
            "memory_recognition_transport",
            "버스",
        ),
        (
            "memory_recognition_place",
            "운동장",
        ),
        (
            "memory_recognition_time",
            "12시",
        ),
        (
            "memory_recognition_activity",
            "축구",
        ),
    ],
)
def test_scores_incorrect_recognition_answer(
    service: ObjectiveScoringService,
    question_code: str,
    transcript: str,
) -> None:
    result = service.score(
        question_code=question_code,
        raw_transcript=transcript,
        assessment_local_date=ASSESSMENT_DATE,
    )

    assert result.answer_status == "incorrect"
    assert result.wrong_event == 1


def test_rejects_multiple_recognition_options(
    service: ObjectiveScoringService,
) -> None:
    result = service.score(
        question_code=(
            "memory_recognition_person"
        ),
        raw_transcript=(
            "영수 아니고 민수입니다."
        ),
        assessment_local_date=ASSESSMENT_DATE,
    )

    assert result.answer_status == "incorrect"
    assert result.wrong_event == 1


@pytest.mark.parametrize(
    "question_code",
    [
        "orientation_place",
        "memory_registration_first",
        "memory_registration_second",
        "memory_delayed_free_recall",
        "language_semantic_fluency",
    ],
)
def test_rejects_unscored_question(
    service: ObjectiveScoringService,
    question_code: str,
) -> None:
    with pytest.raises(
        ValueError,
        match="자동 정오 판정 대상이 아닌",
    ):
        service.score(
            question_code=question_code,
            raw_transcript="응답",
            assessment_local_date=(
                ASSESSMENT_DATE
            ),
        )


def test_rejects_blank_transcript(
    service: ObjectiveScoringService,
) -> None:
    with pytest.raises(
        ValueError,
        match="판정 가능한 전사문",
    ):
        service.score(
            question_code="orientation_year",
            raw_transcript=" ... ",
            assessment_local_date=(
                ASSESSMENT_DATE
            ),
        )


def test_preserves_raw_transcript_outside_service(
    service: ObjectiveScoringService,
) -> None:
    raw_transcript = "  민수입니다!  "

    result = service.score(
        question_code=(
            "memory_recognition_person"
        ),
        raw_transcript=raw_transcript,
        assessment_local_date=ASSESSMENT_DATE,
    )

    assert raw_transcript == "  민수입니다!  "
    assert result.normalized_transcript == (
        "민수입니다"
    )