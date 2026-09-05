import re
from dataclasses import dataclass
from typing import Literal

from app.api.schemas.common import (
    ConditionalQuestionCode,
    MemoryUnitMap,
    SttInput,
)
from app.contracts.models import ContractBundle
from app.text.normalization import (
    compact_answer_text,
    normalize_answer_text,
)

MemoryUnitCode = Literal[
    "person",
    "transport",
    "place",
    "time",
    "activity",
]

VadDetectionStatus = Literal[
    "speech_detected",
    "no_speech_detected",
]

RecognitionRetryReason = Literal[
    "INCOMPLETE_ASSESSMENT",
    "UNSCORABLE_STT",
]

_MEMORY_UNIT_CODES: tuple[
    MemoryUnitCode,
    ...,
] = (
    "person",
    "transport",
    "place",
    "time",
    "activity",
)


@dataclass(frozen=True, slots=True)
class RecognitionPlanCompletedDecision:
    status: Literal["completed"]
    normalized_transcript: str
    recalled_units: MemoryUnitMap
    next_question_codes: tuple[
        ConditionalQuestionCode,
        ...,
    ]


@dataclass(frozen=True, slots=True)
class RecognitionPlanNeedsRetryDecision:
    status: Literal["needs_retry"]
    reason_code: RecognitionRetryReason


RecognitionPlanDecision = (
    RecognitionPlanCompletedDecision
    | RecognitionPlanNeedsRetryDecision
)


@dataclass(frozen=True, slots=True)
class _MemoryUnitRule:
    unit_code: MemoryUnitCode
    normalized_values: tuple[str, ...]
    recognition_question_code: (
        ConditionalQuestionCode
    )


class RecognitionPlanService:
    def __init__(
        self,
        *,
        unit_rules: tuple[
            _MemoryUnitRule,
            ...,
        ],
    ) -> None:
        if not unit_rules:
            raise ValueError(
                "기억 단위 규칙이 하나 이상 필요합니다.",
            )

        if tuple(
            rule.unit_code
            for rule in unit_rules
        ) != _MEMORY_UNIT_CODES:
            raise ValueError(
                "기억 단위 순서가 계약과 "
                "일치하지 않습니다.",
            )

        self._unit_rules = unit_rules

    @classmethod
    def from_contract_bundle(
        cls,
        bundle: ContractBundle,
    ) -> "RecognitionPlanService":
        policy = (
            bundle
            .wrong_event
            .operational_runtime_contract
            .explicit_failure_event_policy
        )
        policy_extra = policy.model_extra or {}

        raw_unit_codes = policy_extra.get(
            "memory_unit_codes",
        )
        raw_unit_values = policy_extra.get(
            "memory_unit_values",
        )

        if not isinstance(
            raw_unit_codes,
            list,
        ):
            raise ValueError(
                "memory_unit_codes가 "
                "누락되었습니다.",
            )

        if tuple(raw_unit_codes) != (
            _MEMORY_UNIT_CODES
        ):
            raise ValueError(
                "memory_unit_codes가 계약과 "
                "일치하지 않습니다.",
            )

        if not isinstance(
            raw_unit_values,
            dict,
        ):
            raise ValueError(
                "memory_unit_values가 "
                "누락되었습니다.",
            )

        if set(raw_unit_values) != set(
            _MEMORY_UNIT_CODES,
        ):
            raise ValueError(
                "memory_unit_values의 기억 단위가 "
                "계약과 일치하지 않습니다.",
            )

        story_units = (
            bundle.cist.memory_story.units
        )

        if tuple(
            unit.unit_code
            for unit in story_units
        ) != _MEMORY_UNIT_CODES:
            raise ValueError(
                "CIST 기억 이야기 단위 순서가 "
                "계약과 일치하지 않습니다.",
            )

        conditional_codes = set(
            bundle
            .cist
            .administration
            .conditional_question_codes,
        )

        unit_rules: list[
            _MemoryUnitRule
        ] = []

        for story_unit in story_units:
            unit_code = story_unit.unit_code
            values = raw_unit_values.get(
                unit_code,
            )

            if not isinstance(
                values,
                list,
            ) or not all(
                isinstance(value, str)
                for value in values
            ):
                raise ValueError(
                    "기억 단위 값이 올바르지 않습니다: "
                    f"{unit_code}",
                )

            normalized_values = tuple(
                dict.fromkeys(
                    normalized_value
                    for value in values
                    if (
                        normalized_value
                        := compact_answer_text(
                            value,
                        )
                    )
                ),
            )

            if not normalized_values:
                raise ValueError(
                    "기억 단위 값이 비어 있습니다: "
                    f"{unit_code}",
                )

            expected_answer = (
                compact_answer_text(
                    story_unit.expected_answer,
                )
            )

            if (
                expected_answer
                not in normalized_values
            ):
                raise ValueError(
                    "CIST 기준 정답이 기억 단위 값에 "
                    "포함되지 않았습니다: "
                    f"{unit_code}",
                )

            recognition_code = (
                story_unit
                .recognition_question_code
            )

            if (
                recognition_code
                not in conditional_codes
            ):
                raise ValueError(
                    "재인 문항 코드가 조건부 문항에 "
                    "포함되지 않았습니다: "
                    f"{recognition_code}",
                )

            unit_rules.append(
                _MemoryUnitRule(
                    unit_code=unit_code,
                    normalized_values=(
                        normalized_values
                    ),
                    recognition_question_code=(
                        recognition_code
                    ),
                ),
            )

        return cls(
            unit_rules=tuple(unit_rules),
        )

    def create_plan(
        self,
        *,
        stt: SttInput,
        vad_status: VadDetectionStatus,
    ) -> RecognitionPlanDecision:
        if vad_status not in {
            "speech_detected",
            "no_speech_detected",
        }:
            raise ValueError(
                "지원하지 않는 VAD 상태입니다: "
                f"{vad_status}",
            )

        if stt.status == "failed":
            return (
                RecognitionPlanNeedsRetryDecision(
                    status="needs_retry",
                    reason_code="UNSCORABLE_STT",
                )
            )

        if stt.status == "empty_transcript":
            if (
                vad_status
                == "no_speech_detected"
            ):
                reason_code = (
                    "INCOMPLETE_ASSESSMENT"
                )
            else:
                reason_code = "UNSCORABLE_STT"

            return (
                RecognitionPlanNeedsRetryDecision(
                    status="needs_retry",
                    reason_code=reason_code,
                )
            )

        if (
            vad_status
            == "no_speech_detected"
        ):
            # STT에는 내용이 있지만 VAD는 발화를 찾지
            # 못한 모순 상태이므로 모든 단위를 누락으로
            # 가정하지 않고 재시도를 요청한다.
            return (
                RecognitionPlanNeedsRetryDecision(
                    status="needs_retry",
                    reason_code="UNSCORABLE_STT",
                )
            )

        raw_transcript = stt.raw_transcript

        if raw_transcript is None:
            return (
                RecognitionPlanNeedsRetryDecision(
                    status="needs_retry",
                    reason_code="UNSCORABLE_STT",
                )
            )

        normalized_transcript = (
            normalize_answer_text(
                raw_transcript,
            )
        )
        compact_transcript = (
            compact_answer_text(
                raw_transcript,
            )
        )

        if not compact_transcript:
            return (
                RecognitionPlanNeedsRetryDecision(
                    status="needs_retry",
                    reason_code="UNSCORABLE_STT",
                )
            )

        recalled_by_code = {
            rule.unit_code: any(
                _contains_memory_value(
                    compact_transcript,
                    value,
                )
                for value in (
                    rule.normalized_values
                )
            )
            for rule in self._unit_rules
        }

        recalled_units = MemoryUnitMap(
            **recalled_by_code,
        )

        next_question_codes = tuple(
            rule.recognition_question_code
            for rule in self._unit_rules
            if not recalled_by_code[
                rule.unit_code
            ]
        )

        return (
            RecognitionPlanCompletedDecision(
                status="completed",
                normalized_transcript=(
                    normalized_transcript
                ),
                recalled_units=recalled_units,
                next_question_codes=(
                    next_question_codes
                ),
            )
        )


def _contains_memory_value(
    compact_transcript: str,
    normalized_value: str,
) -> bool:
    # 숫자가 들어간 값은 211시 안의 11시처럼
    # 더 큰 숫자의 일부로 잘못 탐지하지 않는다.
    if any(
        character.isdecimal()
        for character in normalized_value
    ):
        pattern = re.compile(
            rf"(?<!\d)"
            rf"{re.escape(normalized_value)}"
            rf"(?!\d)",
        )
        return (
            pattern.search(
                compact_transcript,
            )
            is not None
        )

    return (
        normalized_value
        in compact_transcript
    )