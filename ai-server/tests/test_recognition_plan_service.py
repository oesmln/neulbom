from pathlib import Path

import pytest

from app.api.schemas.common import SttInput
from app.contracts.loader import (
    load_contract_bundle,
)
from app.services.recognition_plan import (
    RecognitionPlanCompletedDecision,
    RecognitionPlanNeedsRetryDecision,
    RecognitionPlanService,
)


@pytest.fixture
def service() -> RecognitionPlanService:
    project_root = (
        Path(__file__).resolve().parents[1]
    )
    bundle = load_contract_bundle(
        project_root / "contracts",
    )

    return (
        RecognitionPlanService
        .from_contract_bundle(bundle)
    )


def test_creates_empty_plan_when_all_units_recalled(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=_successful_stt(
            "민수는 자전거를 타고 공원에 가서 "
            "11시부터 야구를 했어요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanCompletedDecision,
    )
    assert decision.recalled_units.model_dump() == {
        "person": True,
        "transport": True,
        "place": True,
        "time": True,
        "activity": True,
    }
    assert decision.next_question_codes == ()


def test_selects_only_questions_for_missing_units(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=_successful_stt(
            "민수는 공원에 가서 야구를 했어요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanCompletedDecision,
    )
    assert decision.recalled_units.model_dump() == {
        "person": True,
        "transport": False,
        "place": True,
        "time": False,
        "activity": True,
    }
    assert decision.next_question_codes == (
        "memory_recognition_transport",
        "memory_recognition_time",
    )


@pytest.mark.parametrize(
    "time_expression",
    [
        "11시",
        "11 시",
        "열한시",
        "열한 시",
        "십일 시",
    ],
)
def test_recognizes_equivalent_time_expressions(
    service: RecognitionPlanService,
    time_expression: str,
) -> None:
    decision = service.create_plan(
        stt=_successful_stt(
            f"민수 {time_expression}",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanCompletedDecision,
    )
    assert decision.recalled_units.person is True
    assert decision.recalled_units.time is True


def test_does_not_match_time_inside_larger_number(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=_successful_stt(
            "211시라고 했어요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanCompletedDecision,
    )
    assert decision.recalled_units.time is False


def test_usable_transcript_with_no_units_selects_all(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=_successful_stt(
            "오늘 날씨가 참 좋네요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanCompletedDecision,
    )
    assert not any(
        decision
        .recalled_units
        .model_dump()
        .values(),
    )
    assert decision.next_question_codes == (
        "memory_recognition_person",
        "memory_recognition_transport",
        "memory_recognition_place",
        "memory_recognition_time",
        "memory_recognition_activity",
    )


def test_failure_expression_still_creates_plan(
    service: RecognitionPlanService,
) -> None:
    # 실패 표현의 wrong-event 판정은 18번에서
    # 별도로 구현한다. STT가 사용 가능하면
    # recognition plan 자체는 생성한다.
    decision = service.create_plan(
        stt=_successful_stt(
            "기억이 안 나요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanCompletedDecision,
    )
    assert len(
        decision.next_question_codes,
    ) == 5


def test_empty_stt_with_no_speech_is_incomplete(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=SttInput(
            status="empty_transcript",
            raw_transcript=None,
        ),
        vad_status="no_speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "INCOMPLETE_ASSESSMENT"
    )


def test_empty_stt_with_speech_is_unscorable(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=SttInput(
            status="empty_transcript",
            raw_transcript=None,
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "UNSCORABLE_STT"
    )


@pytest.mark.parametrize(
    "vad_status",
    [
        "speech_detected",
        "no_speech_detected",
    ],
)
def test_failed_stt_is_unscorable(
    service: RecognitionPlanService,
    vad_status: str,
) -> None:
    decision = service.create_plan(
        stt=SttInput(
            status="failed",
            raw_transcript=None,
        ),
        vad_status=vad_status,
    )

    assert isinstance(
        decision,
        RecognitionPlanNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "UNSCORABLE_STT"
    )


def test_successful_stt_and_missing_vad_conflict(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=_successful_stt(
            "민수",
        ),
        vad_status="no_speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "UNSCORABLE_STT"
    )


def test_empty_transcript_never_assumes_all_units_missing(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=SttInput(
            status="empty_transcript",
            raw_transcript="",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanNeedsRetryDecision,
    )
    assert not hasattr(
        decision,
        "recalled_units",
    )
    assert not hasattr(
        decision,
        "next_question_codes",
    )


def test_punctuation_only_success_is_unscorable(
    service: RecognitionPlanService,
) -> None:
    decision = service.create_plan(
        stt=_successful_stt(
            "... !!!",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        RecognitionPlanNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "UNSCORABLE_STT"
    )


def _successful_stt(
    transcript: str,
) -> SttInput:
    return SttInput(
        status="success",
        raw_transcript=transcript,
    )