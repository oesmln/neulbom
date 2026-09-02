from pathlib import Path

import pytest

from app.api.schemas.common import SttInput
from app.contracts.loader import (
    load_contract_bundle,
)
from app.scoring.memory_failure import (
    MemoryFailureCompletedDecision,
    MemoryFailureNeedsRetryDecision,
    MemoryFailureScoringService,
)

_MEMORY_QUESTION_CODES = (
    "memory_registration_first",
    "memory_registration_second",
    "memory_delayed_free_recall",
)


@pytest.fixture
def service() -> MemoryFailureScoringService:
    project_root = (
        Path(__file__).resolve().parents[1]
    )
    bundle = load_contract_bundle(
        project_root / "contracts",
    )

    return (
        MemoryFailureScoringService
        .from_contract_bundle(bundle)
    )


@pytest.mark.parametrize(
    "question_code",
    _MEMORY_QUESTION_CODES,
)
def test_recalled_unit_produces_no_wrong_event(
    service: MemoryFailureScoringService,
    question_code: str,
) -> None:
    decision = service.score(
        question_code=question_code,
        stt=_successful_stt(
            "민수가 자전거를 탔어요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureCompletedDecision,
    )
    assert decision.scoring_status == "not_scored"
    assert decision.answer_status is None
    assert decision.wrong_event == 0
    assert decision.wrong_event_reason is None
    assert (
        decision
        .recognized_memory_units
        .person
        is True
    )
    assert (
        decision
        .recognized_memory_units
        .transport
        is True
    )


@pytest.mark.parametrize(
    "transcript",
    [
        "모르겠어요.",
        "몰라.",
        "기억이 안 나요.",
        "생각이 안 나요.",
        "못 하겠어요.",
        "할 수 없어요.",
        "잊어버렸어요.",
        "다 잊었어요.",
    ],
)
def test_explicit_failure_expression_produces_event(
    service: MemoryFailureScoringService,
    transcript: str,
) -> None:
    decision = service.score(
        question_code=(
            "memory_delayed_free_recall"
        ),
        stt=_successful_stt(transcript),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureCompletedDecision,
    )
    assert decision.wrong_event == 1
    assert (
        decision.wrong_event_reason
        == "EXPLICIT_FAILURE_EXPRESSION"
    )


def test_explicit_failure_has_priority_over_recalled_unit(
    service: MemoryFailureScoringService,
) -> None:
    decision = service.score(
        question_code=(
            "memory_delayed_free_recall"
        ),
        stt=_successful_stt(
            "민수는 기억나는데 나머지는 모르겠어요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureCompletedDecision,
    )
    assert (
        decision
        .recognized_memory_units
        .person
        is True
    )
    assert decision.wrong_event == 1
    assert (
        decision.wrong_event_reason
        == "EXPLICIT_FAILURE_EXPRESSION"
    )


def test_zero_memory_units_produces_event(
    service: MemoryFailureScoringService,
) -> None:
    decision = service.score(
        question_code=(
            "memory_registration_first"
        ),
        stt=_successful_stt(
            "오늘 날씨가 참 좋네요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureCompletedDecision,
    )
    assert not any(
        decision
        .recognized_memory_units
        .model_dump()
        .values(),
    )
    assert decision.wrong_event == 1
    assert (
        decision.wrong_event_reason
        == "NO_RELEVANT_MEMORY_UNIT"
    )


def test_partial_recall_is_not_formal_incorrect(
    service: MemoryFailureScoringService,
) -> None:
    decision = service.score(
        question_code=(
            "memory_delayed_free_recall"
        ),
        stt=_successful_stt(
            "민수하고 공원이요.",
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureCompletedDecision,
    )
    assert decision.scoring_status == "not_scored"
    assert decision.answer_status is None
    assert decision.wrong_event == 0
    assert decision.wrong_event_reason is None


def test_no_response_requests_retry_without_event(
    service: MemoryFailureScoringService,
) -> None:
    decision = service.score(
        question_code=(
            "memory_registration_second"
        ),
        stt=SttInput(
            status="empty_transcript",
            raw_transcript=None,
        ),
        vad_status="no_speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "INCOMPLETE_ASSESSMENT"
    )
    assert decision.wrong_event is None
    assert decision.recognized_memory_units is None


def test_empty_stt_with_speech_requests_retry(
    service: MemoryFailureScoringService,
) -> None:
    decision = service.score(
        question_code=(
            "memory_delayed_free_recall"
        ),
        stt=SttInput(
            status="empty_transcript",
            raw_transcript=None,
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "UNSCORABLE_STT"
    )
    assert decision.wrong_event is None


def test_failed_stt_requests_retry(
    service: MemoryFailureScoringService,
) -> None:
    decision = service.score(
        question_code=(
            "memory_registration_first"
        ),
        stt=SttInput(
            status="failed",
            raw_transcript=None,
        ),
        vad_status="speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "UNSCORABLE_STT"
    )
    assert decision.wrong_event is None


def test_successful_stt_and_no_speech_is_unscorable(
    service: MemoryFailureScoringService,
) -> None:
    decision = service.score(
        question_code=(
            "memory_registration_first"
        ),
        stt=_successful_stt("민수"),
        vad_status="no_speech_detected",
    )

    assert isinstance(
        decision,
        MemoryFailureNeedsRetryDecision,
    )
    assert (
        decision.reason_code
        == "UNSCORABLE_STT"
    )
    assert decision.wrong_event is None


def test_rejects_non_memory_failure_question(
    service: MemoryFailureScoringService,
) -> None:
    with pytest.raises(
        ValueError,
        match="판정 대상이 아닌",
    ):
        service.score(
            question_code="orientation_year",
            stt=_successful_stt("2026년"),
            vad_status="speech_detected",
        )


def _successful_stt(
    transcript: str,
) -> SttInput:
    return SttInput(
        status="success",
        raw_transcript=transcript,
    )