from collections.abc import Iterable
from dataclasses import dataclass
from typing import Literal

from app.contracts.models import ContractBundle

WrongEventValue = Literal[0, 1] | None


@dataclass(frozen=True, slots=True)
class WrongEventObservation:
    question_code: str
    wrong_event: WrongEventValue


@dataclass(frozen=True, slots=True)
class WrongEventAggregationResult:
    category_event_counts: dict[str, int]
    category_wrong_event_scores: dict[str, float]
    category_balanced_wrong_event_score: float


class WrongEventAggregationService:
    def __init__(
        self,
        *,
        category_order: tuple[str, ...],
        question_category_map: dict[str, str],
        count_cap: int,
    ) -> None:
        if not category_order:
            raise ValueError(
                "핵심 범주가 하나 이상 필요합니다.",
            )

        if len(set(category_order)) != len(
            category_order,
        ):
            raise ValueError(
                "핵심 범주가 중복되었습니다.",
            )

        if count_cap <= 0:
            raise ValueError(
                "범주별 wrong event 상한은 "
                "1 이상이어야 합니다.",
            )

        if not question_category_map:
            raise ValueError(
                "문항별 범주 매핑이 필요합니다.",
            )

        unknown_categories = (
            set(question_category_map.values())
            - set(category_order)
        )

        if unknown_categories:
            raise ValueError(
                "문항에 알 수 없는 범주가 "
                f"지정되었습니다: {sorted(unknown_categories)}",
            )

        self._category_order = category_order
        self._question_category_map = dict(
            question_category_map,
        )
        self._count_cap = count_cap

    @classmethod
    def from_contract_bundle(
        cls,
        bundle: ContractBundle,
    ) -> "WrongEventAggregationService":
        policy = (
            bundle
            .wrong_event
            .operational_runtime_contract
            .aggregation
        )
        policy_extra = policy.model_extra or {}

        category_order = tuple(
            bundle.cist.core_categories,
        )

        if (
            len(category_order)
            != policy.required_category_count
        ):
            raise ValueError(
                "핵심 범주 개수가 집계 계약과 "
                "일치하지 않습니다.",
            )

        category_mapping = dict(
            policy.category_mapping,
        )

        if set(category_mapping) != set(
            category_order,
        ):
            raise ValueError(
                "범주 매핑의 입력 범주가 CIST 계약과 "
                "일치하지 않습니다.",
            )

        if set(category_mapping.values()) != set(
            category_order,
        ):
            raise ValueError(
                "범주 매핑의 출력 범주가 CIST 계약과 "
                "일치하지 않습니다.",
            )

        if (
            policy_extra.get(
                "initialize_all_core_category_event_counts_to_zero",
            )
            is not True
        ):
            raise ValueError(
                "핵심 범주 초기화 규칙이 "
                "계약과 일치하지 않습니다.",
            )

        if (
            policy_extra.get(
                "conditional_not_applicable_items_are_excluded",
            )
            is not True
        ):
            raise ValueError(
                "조건부 미시행 문항 제외 규칙이 "
                "계약과 일치하지 않습니다.",
            )

        if (
            policy_extra.get(
                "null_wrong_events_are_not_added",
            )
            is not True
        ):
            raise ValueError(
                "null wrong event 제외 규칙이 "
                "계약과 일치하지 않습니다.",
            )

        count_cap = policy_extra.get(
            "wrong_event_count_cap_per_category",
        )

        if (
            not isinstance(count_cap, int)
            or isinstance(count_cap, bool)
            or count_cap <= 0
        ):
            raise ValueError(
                "범주별 wrong event 상한이 "
                "올바르지 않습니다.",
            )

        question_category_map = {
            question.question_code: (
                category_mapping[
                    question.question_type
                ]
            )
            for question in bundle.cist.questions
        }

        return cls(
            category_order=category_order,
            question_category_map=(
                question_category_map
            ),
            count_cap=count_cap,
        )

    def aggregate(
        self,
        observations: Iterable[
            WrongEventObservation
        ],
    ) -> WrongEventAggregationResult:
        category_event_counts = {
            category: 0
            for category in self._category_order
        }
        observed_question_codes: set[str] = set()

        for observation in observations:
            question_code = observation.question_code

            if (
                question_code
                in observed_question_codes
            ):
                raise ValueError(
                    "동일한 문항의 wrong event 결과가 "
                    f"중복되었습니다: {question_code}",
                )

            observed_question_codes.add(
                question_code,
            )

            category = (
                self._question_category_map.get(
                    question_code,
                )
            )

            if category is None:
                raise ValueError(
                    "CIST 계약에 없는 문항입니다: "
                    f"{question_code}",
                )

            wrong_event = observation.wrong_event

            if wrong_event is None:
                continue

            if (
                isinstance(wrong_event, bool)
                or wrong_event not in {0, 1}
            ):
                raise ValueError(
                    "wrong_event는 0, 1 또는 "
                    "null이어야 합니다.",
                )

            if wrong_event == 1:
                category_event_counts[
                    category
                ] += 1

        category_wrong_event_scores = {
            category: (
                min(
                    category_event_counts[
                        category
                    ],
                    self._count_cap,
                )
                / self._count_cap
            )
            for category in self._category_order
        }

        balanced_score = (
            sum(
                category_wrong_event_scores.values(),
            )
            / len(self._category_order)
        )

        return WrongEventAggregationResult(
            category_event_counts=(
                category_event_counts
            ),
            category_wrong_event_scores=(
                category_wrong_event_scores
            ),
            category_balanced_wrong_event_score=(
                balanced_score
            ),
        )