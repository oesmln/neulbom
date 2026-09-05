from collections.abc import Iterable
from dataclasses import dataclass
from statistics import median

from app.contracts.models import ContractBundle


@dataclass(frozen=True, slots=True)
class ResponseDelayObservation:
    question_code: str
    response_delay_ms: int | None


@dataclass(frozen=True, slots=True)
class ResponseDelayAggregationResult:
    category_median_delay_seconds: dict[
        str,
        float,
    ]
    category_balanced_median_delay: float


class ResponseDelayAggregationService:
    """
    응답 지연을 학습 당시와 동일한 방식으로 집계한다.

    1. 문항별 밀리초 값을 범주별로 모은다.
    2. 각 범주의 중앙값을 계산한다.
    3. 네 범주 중앙값을 동일 비중으로 평균한다.
    4. 최종 결과를 초 단위로 반환한다.
    """

    def __init__(
        self,
        *,
        category_order: tuple[str, ...],
        question_category_map: dict[
            str,
            str,
        ],
    ) -> None:
        if not category_order:
            raise ValueError(
                "핵심 범주가 하나 이상 필요합니다.",
            )

        if len(category_order) != len(
            set(category_order),
        ):
            raise ValueError(
                "핵심 범주가 중복되었습니다.",
            )

        if not question_category_map:
            raise ValueError(
                "응답 지연 대상 문항이 필요합니다.",
            )

        unknown_categories = (
            set(
                question_category_map.values(),
            )
            - set(category_order)
        )

        if unknown_categories:
            raise ValueError(
                "문항에 알 수 없는 범주가 "
                "지정되었습니다: "
                f"{sorted(unknown_categories)}",
            )

        self._category_order = category_order
        self._question_category_map = dict(
            question_category_map,
        )

    @classmethod
    def from_contract_bundle(
        cls,
        bundle: ContractBundle,
    ) -> "ResponseDelayAggregationService":
        category_order = tuple(
            bundle.cist.core_categories,
        )

        question_category_map = {
            question.question_code: (
                question.question_type
            )
            for question in bundle.cist.questions
            if question.feature_usage.response_delay
        }

        observed_categories = set(
            question_category_map.values(),
        )

        if observed_categories != set(
            category_order,
        ):
            raise ValueError(
                "응답 지연 사용 문항이 네 핵심 범주를 "
                "모두 포함하지 않습니다.",
            )

        return cls(
            category_order=category_order,
            question_category_map=(
                question_category_map
            ),
        )

    def aggregate(
        self,
        observations: Iterable[
            ResponseDelayObservation
        ],
    ) -> ResponseDelayAggregationResult:
        delays_by_category: dict[
            str,
            list[int],
        ] = {
            category: []
            for category in self._category_order
        }
        observed_question_codes: set[
            str
        ] = set()

        for observation in observations:
            question_code = (
                observation.question_code
            )

            if (
                question_code
                in observed_question_codes
            ):
                raise ValueError(
                    "응답 지연 문항이 중복되었습니다: "
                    f"{question_code}",
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
                    "응답 지연 사용 대상이 아닌 "
                    f"문항입니다: {question_code}",
                )

            response_delay_ms = (
                observation.response_delay_ms
            )

            # not_applicable 또는 Q05 no_response와 같이
            # 지연을 계산할 수 없는 문항은 제외한다.
            if response_delay_ms is None:
                continue

            if (
                isinstance(
                    response_delay_ms,
                    bool,
                )
                or not isinstance(
                    response_delay_ms,
                    int,
                )
            ):
                raise TypeError(
                    "response_delay_ms는 "
                    "정수 또는 null이어야 합니다.",
                )

            if response_delay_ms < 0:
                raise ValueError(
                    "response_delay_ms는 "
                    "0 이상이어야 합니다.",
                )

            # 0ms도 실제 유효값이므로 그대로 포함한다.
            delays_by_category[
                category
            ].append(
                response_delay_ms,
            )

        missing_categories = [
            category
            for category in self._category_order
            if not delays_by_category[category]
        ]

        if missing_categories:
            raise ValueError(
                "응답 지연을 계산할 수 없는 "
                "핵심 범주가 있습니다: "
                f"{missing_categories}",
            )

        category_medians = {
            category: (
                float(
                    median(
                        delays_by_category[
                            category
                        ],
                    ),
                )
                / 1000.0
            )
            for category in self._category_order
        }

        balanced_median_delay = (
            sum(category_medians.values())
            / len(self._category_order)
        )

        return ResponseDelayAggregationResult(
            category_median_delay_seconds=(
                category_medians
            ),
            category_balanced_median_delay=(
                balanced_median_delay
            ),
        )