import re
from dataclasses import dataclass
from typing import Literal, Pattern

from app.api.schemas.common import (
    MemoryUnitMap,
    SttInput,
)
from app.contracts.models import ContractBundle
from app.services.recognition_plan import (
    RecognitionPlanCompletedDecision,
    RecognitionPlanNeedsRetryDecision,
    RecognitionPlanService,
    RecognitionRetryReason,
    VadDetectionStatus,
)

MemoryFailureQuestionCode = Literal[
    "memory_registration_first",
    "memory_registration_second",
    "memory_delayed_free_recall",
]

MemoryFailureWrongEventReason = Literal[
    "EXPLICIT_FAILURE_EXPRESSION",
    "NO_RELEVANT_MEMORY_UNIT",
]

_MEMORY_FAILURE_QUESTION_CODES: tuple[
    MemoryFailureQuestionCode,
    ...,
] = (
    "memory_registration_first",
    "memory_registration_second",
    "memory_delayed_free_recall",
)


@dataclass(frozen=True, slots=True)
class MemoryFailureCompletedDecision:
    status: Literal["completed"]
    question_code: MemoryFailureQuestionCode
    normalized_transcript: str
    scoring_status: Literal["not_scored"]
    answer_status: None
    wrong_event: Literal[0, 1]
    wrong_event_reason: (
        MemoryFailureWrongEventReason | None
    )
    recognized_memory_units: MemoryUnitMap


@dataclass(frozen=True, slots=True)
class MemoryFailureNeedsRetryDecision:
    status: Literal["needs_retry"]
    question_code: MemoryFailureQuestionCode
    reason_code: RecognitionRetryReason
    scoring_status: Literal["not_scored"]
    answer_status: None
    wrong_event: None
    wrong_event_reason: None
    recognized_memory_units: None


MemoryFailureDecision = (
    MemoryFailureCompletedDecision
    | MemoryFailureNeedsRetryDecision
)


class MemoryFailureScoringService:
    def __init__(
        self,
        *,
        recognition_plan_service: RecognitionPlanService,
        failure_patterns: tuple[Pattern[str], ...],
    ) -> None:
        if not failure_patterns:
            raise ValueError(
                "명시적 실패 표현 규칙이 하나 이상 필요합니다.",
            )

        self._recognition_plan_service = (
            recognition_plan_service
        )
        self._failure_patterns = failure_patterns

    @classmethod
    def from_contract_bundle(
        cls,
        bundle: ContractBundle,
    ) -> "MemoryFailureScoringService":
        policy = (
            bundle
            .wrong_event
            .operational_runtime_contract
            .explicit_failure_event_policy
        )
        policy_extra = policy.model_extra or {}

        if tuple(
            policy.applies_to_question_codes,
        ) != _MEMORY_FAILURE_QUESTION_CODES:
            raise ValueError(
                "보조 실패 사건 대상 문항이 "
                "계약과 일치하지 않습니다.",
            )

        if (
            policy_extra.get(
                "formal_item_scoring",
            )
            is not False
        ):
            raise ValueError(
                "기억 문항은 공식 정오 채점 대상이 "
                "아니어야 합니다.",
            )

        if (
            policy_extra.get(
                "scoring_status",
            )
            != "not_scored"
        ):
            raise ValueError(
                "기억 문항 scoring_status가 "
                "계약과 일치하지 않습니다.",
            )

        if (
            policy_extra.get(
                "answer_status",
            )
            is not None
        ):
            raise ValueError(
                "기억 문항 answer_status는 "
                "null이어야 합니다.",
            )

        if (
            policy_extra.get(
                "partial_recall_is_not_formal_incorrect",
            )
            is not True
        ):
            raise ValueError(
                "부분 회상 처리 규칙이 "
                "계약과 일치하지 않습니다.",
            )

        raw_patterns = policy_extra.get(
            "explicit_failure_expression_patterns",
        )

        if (
            not isinstance(raw_patterns, list)
            or not raw_patterns
            or not all(
                isinstance(pattern, str)
                and pattern
                for pattern in raw_patterns
            )
        ):
            raise ValueError(
                "명시적 실패 표현 규칙이 "
                "누락되었거나 올바르지 않습니다.",
            )

        try:
            failure_patterns = tuple(
                re.compile(pattern)
                for pattern in raw_patterns
            )
        except re.error as error:
            raise ValueError(
                "명시적 실패 표현 정규식이 "
                "올바르지 않습니다.",
            ) from error

        recognition_plan_service = (
            RecognitionPlanService
            .from_contract_bundle(bundle)
        )

        return cls(
            recognition_plan_service=(
                recognition_plan_service
            ),
            failure_patterns=failure_patterns,
        )

    def score(
        self,
        *,
        question_code: MemoryFailureQuestionCode,
        stt: SttInput,
        vad_status: VadDetectionStatus,
    ) -> MemoryFailureDecision:
        if (
            question_code
            not in _MEMORY_FAILURE_QUESTION_CODES
        ):
            raise ValueError(
                "보조 실패 사건 판정 대상이 아닌 "
                f"문항입니다: {question_code}",
            )

        recognition_decision = (
            self._recognition_plan_service
            .create_plan(
                stt=stt,
                vad_status=vad_status,
            )
        )

        if isinstance(
            recognition_decision,
            RecognitionPlanNeedsRetryDecision,
        ):
            return MemoryFailureNeedsRetryDecision(
                status="needs_retry",
                question_code=question_code,
                reason_code=(
                    recognition_decision.reason_code
                ),
                scoring_status="not_scored",
                answer_status=None,
                wrong_event=None,
                wrong_event_reason=None,
                recognized_memory_units=None,
            )

        if not isinstance(
            recognition_decision,
            RecognitionPlanCompletedDecision,
        ):
            raise TypeError(
                "지원하지 않는 기억 단위 판정 결과입니다.",
            )

        normalized_transcript = (
            recognition_decision
            .normalized_transcript
        )
        recognized_memory_units = (
            recognition_decision.recalled_units
        )

        has_explicit_failure = any(
            pattern.search(normalized_transcript)
            is not None
            for pattern in self._failure_patterns
        )

        if has_explicit_failure:
            return MemoryFailureCompletedDecision(
                status="completed",
                question_code=question_code,
                normalized_transcript=(
                    normalized_transcript
                ),
                scoring_status="not_scored",
                answer_status=None,
                wrong_event=1,
                wrong_event_reason=(
                    "EXPLICIT_FAILURE_EXPRESSION"
                ),
                recognized_memory_units=(
                    recognized_memory_units
                ),
            )

        has_memory_unit = any(
            recognized_memory_units
            .model_dump()
            .values(),
        )

        if not has_memory_unit:
            return MemoryFailureCompletedDecision(
                status="completed",
                question_code=question_code,
                normalized_transcript=(
                    normalized_transcript
                ),
                scoring_status="not_scored",
                answer_status=None,
                wrong_event=1,
                wrong_event_reason=(
                    "NO_RELEVANT_MEMORY_UNIT"
                ),
                recognized_memory_units=(
                    recognized_memory_units
                ),
            )

        return MemoryFailureCompletedDecision(
            status="completed",
            question_code=question_code,
            normalized_transcript=(
                normalized_transcript
            ),
            scoring_status="not_scored",
            answer_status=None,
            wrong_event=0,
            wrong_event_reason=None,
            recognized_memory_units=(
                recognized_memory_units
            ),
        )