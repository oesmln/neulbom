from dataclasses import dataclass
from math import sqrt

import pytest

from app.inference.feature_snapshot import (
    CategoryLogitAggregation,
    FeatureAggregationError,
    pool_category_logits_to_person,
    pool_question_logits_by_category,
)

CATEGORY_ORDER = (
    "orientation",
    "memory",
    "attention",
    "language",
)


@dataclass(frozen=True, slots=True)
class QuestionFeature:
    question_code: str
    category: str
    dementia_logit: float


def question_features() -> tuple[
    QuestionFeature,
    ...,
]:
    return (
        QuestionFeature(
            "orientation_year",
            "orientation",
            0.2,
        ),
        QuestionFeature(
            "orientation_month",
            "orientation",
            0.6,
        ),
        QuestionFeature(
            "memory_registration_first",
            "memory",
            0.3,
        ),
        QuestionFeature(
            "attention_digit_span_4",
            "attention",
            0.5,
        ),
        QuestionFeature(
            "language_semantic_fluency",
            "language",
            0.7,
        ),
    )


def test_pools_question_logits_by_category() -> None:
    result = pool_question_logits_by_category(
        question_features(),
        category_order=CATEGORY_ORDER,
    )

    assert tuple(
        feature.category
        for feature in result
    ) == CATEGORY_ORDER
    assert result[0].dementia_logit == (
        pytest.approx(0.4)
    )
    assert result[0].clip_count == 2
    assert tuple(
        feature.dementia_logit
        for feature in result[1:]
    ) == pytest.approx(
        (
            0.3,
            0.5,
            0.7,
        ),
    )


def test_pools_category_logits_with_equal_weight() -> None:
    categories = pool_question_logits_by_category(
        question_features(),
        category_order=CATEGORY_ORDER,
    )

    result = pool_category_logits_to_person(
        categories,
        category_order=CATEGORY_ORDER,
        method="equal_category_mean",
    )

    assert result == pytest.approx(
        (0.4 + 0.3 + 0.5 + 0.7) / 4,
    )


def test_pools_category_logits_with_sqrt_weight() -> None:
    categories = pool_question_logits_by_category(
        question_features(),
        category_order=CATEGORY_ORDER,
    )

    result = pool_category_logits_to_person(
        categories,
        category_order=CATEGORY_ORDER,
        method="sqrt_clip_count_weighted",
    )

    assert result == pytest.approx(
        (
            0.4 * sqrt(2)
            + 0.3
            + 0.5
            + 0.7
        )
        / (sqrt(2) + 3),
    )


def test_rejects_duplicate_question_feature() -> None:
    duplicated = (
        *question_features(),
        QuestionFeature(
            "orientation_year",
            "orientation",
            0.9,
        ),
    )

    with pytest.raises(
        FeatureAggregationError,
        match="중복",
    ):
        pool_question_logits_by_category(
            duplicated,
            category_order=CATEGORY_ORDER,
        )


def test_rejects_missing_core_category() -> None:
    with pytest.raises(
        FeatureAggregationError,
        match="핵심 범주가 누락",
    ):
        pool_question_logits_by_category(
            question_features()[:-1],
            category_order=CATEGORY_ORDER,
        )


@pytest.mark.parametrize(
    ("category", "dementia_logit", "message"),
    (
        (
            "unknown",
            0.1,
            "알 수 없는 범주",
        ),
        (
            "orientation",
            float("nan"),
            "유한한 숫자",
        ),
    ),
)
def test_rejects_invalid_question_feature(
    category: str,
    dementia_logit: float,
    message: str,
) -> None:
    invalid = (
        QuestionFeature(
            "invalid_question",
            category,
            dementia_logit,
        ),
    )

    with pytest.raises(
        FeatureAggregationError,
        match=message,
    ):
        pool_question_logits_by_category(
            invalid,
            category_order=CATEGORY_ORDER,
        )


def test_rejects_invalid_category_clip_count() -> None:
    categories = tuple(
        CategoryLogitAggregation(
            category=category,
            dementia_logit=0.1,
            clip_count=(
                0
                if category == "orientation"
                else 1
            ),
        )
        for category in CATEGORY_ORDER
    )

    with pytest.raises(
        FeatureAggregationError,
        match="clip_count",
    ):
        pool_category_logits_to_person(
            categories,
            category_order=CATEGORY_ORDER,
            method=(
                "sqrt_clip_count_weighted"
            ),
        )
