from collections.abc import Iterable
from dataclasses import dataclass
from math import isfinite, sqrt
from typing import Literal, Protocol, TypeVar

import numpy as np


class QuestionCodedFeature(Protocol):
    question_code: str


class QuestionLogitFeature(
    QuestionCodedFeature,
    Protocol,
):
    category: str
    dementia_logit: float


class CategoryLogitFeature(Protocol):
    category: str
    dementia_logit: float
    clip_count: int


PersonPoolingMethod = Literal[
    "equal_category_mean",
    "sqrt_clip_count_weighted",
]

QuestionFeatureT = TypeVar(
    "QuestionFeatureT",
    bound=QuestionCodedFeature,
)


class FeatureAggregationError(ValueError):
    """문항 특징을 범주·대상자 단위로 집계할 수 없는 경우."""


@dataclass(frozen=True, slots=True)
class CategoryLogitAggregation:
    category: str
    dementia_logit: float
    clip_count: int


def pool_question_logits_by_category(
    question_features: Iterable[
        QuestionLogitFeature
    ],
    *,
    category_order: tuple[str, ...],
) -> tuple[CategoryLogitAggregation, ...]:
    """문항별 logit을 범주별 동일 가중 평균으로 집계한다."""

    if not category_order:
        raise FeatureAggregationError(
            "핵심 범주가 하나 이상 필요합니다.",
        )

    if len(category_order) != len(
        set(category_order),
    ):
        raise FeatureAggregationError(
            "핵심 범주가 중복되었습니다.",
        )

    by_category: dict[
        str,
        list[QuestionLogitFeature],
    ] = {
        category: []
        for category in category_order
    }
    observed_question_codes: set[str] = set()

    for feature in question_features:
        if (
            feature.question_code
            in observed_question_codes
        ):
            raise FeatureAggregationError(
                "문항 특징이 중복되었습니다: "
                f"{feature.question_code}",
            )

        observed_question_codes.add(
            feature.question_code,
        )

        if feature.category not in by_category:
            raise FeatureAggregationError(
                "문항 특징에 알 수 없는 범주가 "
                "지정되었습니다: "
                f"{feature.category}",
            )

        if not isfinite(
            feature.dementia_logit,
        ):
            raise FeatureAggregationError(
                "문항 logit은 유한한 숫자여야 "
                "합니다.",
            )

        by_category[feature.category].append(
            feature,
        )

    missing_categories = [
        category
        for category in category_order
        if not by_category[category]
    ]

    if missing_categories:
        raise FeatureAggregationError(
            "핵심 범주가 누락되었습니다: "
            f"{missing_categories}",
        )

    return tuple(
        CategoryLogitAggregation(
            category=category,
            dementia_logit=float(
                np.mean(
                    [
                        feature.dementia_logit
                        for feature
                        in by_category[category]
                    ],
                    dtype=np.float64,
                ),
            ),
            clip_count=len(
                by_category[category],
            ),
        )
        for category in category_order
    )


def pool_category_logits_to_person(
    category_features: Iterable[
        CategoryLogitFeature
    ],
    *,
    category_order: tuple[str, ...],
    method: PersonPoolingMethod,
) -> float:
    """범주별 logit을 모델 계약에 맞는 대상자 logit으로 집계한다."""

    features = tuple(category_features)

    if method not in {
        "equal_category_mean",
        "sqrt_clip_count_weighted",
    }:
        raise FeatureAggregationError(
            "지원하지 않는 대상자 집계 방식입니다: "
            f"{method}",
        )

    if not category_order:
        raise FeatureAggregationError(
            "핵심 범주가 하나 이상 필요합니다.",
        )

    if len(category_order) != len(
        set(category_order),
    ):
        raise FeatureAggregationError(
            "핵심 범주가 중복되었습니다.",
        )

    by_category: dict[
        str,
        CategoryLogitFeature,
    ] = {}

    for feature in features:
        if feature.category in by_category:
            raise FeatureAggregationError(
                "범주 특징이 중복되었습니다: "
                f"{feature.category}",
            )

        if feature.category not in category_order:
            raise FeatureAggregationError(
                "알 수 없는 범주 특징입니다: "
                f"{feature.category}",
            )

        if not isfinite(
            feature.dementia_logit,
        ):
            raise FeatureAggregationError(
                "범주 logit은 유한한 숫자여야 "
                "합니다.",
            )

        if (
            isinstance(feature.clip_count, bool)
            or not isinstance(
                feature.clip_count,
                int,
            )
            or feature.clip_count <= 0
        ):
            raise FeatureAggregationError(
                "범주 clip_count는 1 이상이어야 "
                "합니다.",
            )

        by_category[feature.category] = feature

    missing_categories = [
        category
        for category in category_order
        if category not in by_category
    ]

    if missing_categories:
        raise FeatureAggregationError(
            "핵심 범주가 누락되었습니다: "
            f"{missing_categories}",
        )

    ordered_features = tuple(
        by_category[category]
        for category in category_order
    )
    logits = np.asarray(
        [
            feature.dementia_logit
            for feature in ordered_features
        ],
        dtype=np.float64,
    )

    if method == "equal_category_mean":
        person_logit = float(
            np.mean(
                logits,
                dtype=np.float64,
            ),
        )
    else:
        weights = np.asarray(
            [
                sqrt(feature.clip_count)
                for feature in ordered_features
            ],
            dtype=np.float64,
        )
        person_logit = float(
            np.average(
                logits,
                weights=weights,
            ),
        )

    if not isfinite(person_logit):
        raise FeatureAggregationError(
            "대상자 logit이 유효하지 않습니다.",
        )

    return person_logit


def rebuild_person_logit_from_questions(
    question_features: Iterable[
        QuestionLogitFeature
    ],
    *,
    category_order: tuple[str, ...],
    method: PersonPoolingMethod,
) -> float:
    category_features = (
        pool_question_logits_by_category(
            question_features,
            category_order=category_order,
        )
    )

    return pool_category_logits_to_person(
        category_features,
        category_order=category_order,
        method=method,
    )


def replace_question_features(
    current_features: Iterable[
        QuestionFeatureT
    ],
    replacement_features: Iterable[
        QuestionFeatureT
    ],
) -> tuple[QuestionFeatureT, ...]:
    """기존 순서를 유지하며 동일 question_code의 특징만 교체한다."""

    current = tuple(current_features)
    replacements = tuple(
        replacement_features,
    )
    current_by_code = _index_by_question_code(
        current,
        label="기존",
    )
    replacement_by_code = (
        _index_by_question_code(
            replacements,
            label="교체",
        )
    )
    unknown_codes = (
        set(replacement_by_code)
        - set(current_by_code)
    )

    if unknown_codes:
        raise FeatureAggregationError(
            "기존 스냅샷에 없는 문항 특징은 "
            "교체할 수 없습니다: "
            f"{sorted(unknown_codes)}",
        )

    return tuple(
        replacement_by_code.get(
            feature.question_code,
            feature,
        )
        for feature in current
    )


def _index_by_question_code(
    features: tuple[QuestionFeatureT, ...],
    *,
    label: str,
) -> dict[str, QuestionFeatureT]:
    indexed = {
        feature.question_code: feature
        for feature in features
    }

    if len(indexed) != len(features):
        raise FeatureAggregationError(
            f"{label} 문항 특징이 중복되었습니다.",
        )

    return indexed
