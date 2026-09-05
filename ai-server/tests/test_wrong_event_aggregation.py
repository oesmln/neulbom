from pathlib import Path

import pytest

from app.contracts.loader import (
    load_contract_bundle,
)
from app.scoring.aggregation import (
    WrongEventAggregationService,
    WrongEventObservation,
)


@pytest.fixture
def service() -> WrongEventAggregationService:
    project_root = (
        Path(__file__).resolve().parents[1]
    )
    bundle = load_contract_bundle(
        project_root / "contracts",
    )

    return (
        WrongEventAggregationService
        .from_contract_bundle(bundle)
    )


def test_initializes_all_categories_to_zero(
    service: WrongEventAggregationService,
) -> None:
    result = service.aggregate([])

    assert result.category_event_counts == {
        "orientation": 0,
        "memory": 0,
        "attention": 0,
        "language": 0,
    }
    assert (
        result.category_wrong_event_scores
        == {
            "orientation": 0.0,
            "memory": 0.0,
            "attention": 0.0,
            "language": 0.0,
        }
    )
    assert (
        result
        .category_balanced_wrong_event_score
        == 0.0
    )


def test_counts_events_by_question_category(
    service: WrongEventAggregationService,
) -> None:
    result = service.aggregate(
        [
            WrongEventObservation(
                question_code=(
                    "orientation_year"
                ),
                wrong_event=1,
            ),
            WrongEventObservation(
                question_code=(
                    "memory_registration_first"
                ),
                wrong_event=1,
            ),
            WrongEventObservation(
                question_code=(
                    "attention_digit_span_4"
                ),
                wrong_event=1,
            ),
        ],
    )

    assert result.category_event_counts == {
        "orientation": 1,
        "memory": 1,
        "attention": 1,
        "language": 0,
    }
    assert (
        result.category_wrong_event_scores
        == {
            "orientation": 0.5,
            "memory": 0.5,
            "attention": 0.5,
            "language": 0.0,
        }
    )
    assert (
        result
        .category_balanced_wrong_event_score
        == pytest.approx(0.375)
    )


def test_caps_each_category_at_two_events(
    service: WrongEventAggregationService,
) -> None:
    result = service.aggregate(
        [
            WrongEventObservation(
                question_code=(
                    "orientation_year"
                ),
                wrong_event=1,
            ),
            WrongEventObservation(
                question_code=(
                    "orientation_month"
                ),
                wrong_event=1,
            ),
            WrongEventObservation(
                question_code=(
                    "orientation_day"
                ),
                wrong_event=1,
            ),
        ],
    )

    assert (
        result.category_event_counts[
            "orientation"
        ]
        == 3
    )
    assert (
        result.category_wrong_event_scores[
            "orientation"
        ]
        == 1.0
    )
    assert (
        result
        .category_balanced_wrong_event_score
        == pytest.approx(0.25)
    )


def test_null_and_zero_are_not_added(
    service: WrongEventAggregationService,
) -> None:
    result = service.aggregate(
        [
            WrongEventObservation(
                question_code=(
                    "orientation_place"
                ),
                wrong_event=None,
            ),
            WrongEventObservation(
                question_code=(
                    "orientation_year"
                ),
                wrong_event=0,
            ),
            WrongEventObservation(
                question_code=(
                    "language_semantic_fluency"
                ),
                wrong_event=None,
            ),
        ],
    )

    assert result.category_event_counts == {
        "orientation": 0,
        "memory": 0,
        "attention": 0,
        "language": 0,
    }
    assert (
        result
        .category_balanced_wrong_event_score
        == 0.0
    )


def test_reproduces_reference_balanced_score(
    service: WrongEventAggregationService,
) -> None:
    result = service.aggregate(
        [
            WrongEventObservation(
                question_code=(
                    "orientation_year"
                ),
                wrong_event=1,
            ),
            WrongEventObservation(
                question_code=(
                    "orientation_month"
                ),
                wrong_event=1,
            ),
            WrongEventObservation(
                question_code=(
                    "orientation_day"
                ),
                wrong_event=1,
            ),
            WrongEventObservation(
                question_code=(
                    "memory_registration_first"
                ),
                wrong_event=1,
            ),
            WrongEventObservation(
                question_code=(
                    "attention_digit_span_4"
                ),
                wrong_event=0,
            ),
            WrongEventObservation(
                question_code=(
                    "language_semantic_fluency"
                ),
                wrong_event=None,
            ),
        ],
    )

    assert (
        result.category_wrong_event_scores[
            "orientation"
        ]
        == 1.0
    )
    assert (
        result.category_wrong_event_scores[
            "memory"
        ]
        == 0.5
    )
    assert (
        result.category_wrong_event_scores[
            "attention"
        ]
        == 0.0
    )
    assert (
        result.category_wrong_event_scores[
            "language"
        ]
        == 0.0
    )
    assert (
        result
        .category_balanced_wrong_event_score
        == pytest.approx(0.375)
    )


def test_rejects_duplicate_question_results(
    service: WrongEventAggregationService,
) -> None:
    observations = [
        WrongEventObservation(
            question_code="orientation_year",
            wrong_event=0,
        ),
        WrongEventObservation(
            question_code="orientation_year",
            wrong_event=1,
        ),
    ]

    with pytest.raises(
        ValueError,
        match="중복",
    ):
        service.aggregate(observations)


def test_rejects_unknown_question_code(
    service: WrongEventAggregationService,
) -> None:
    with pytest.raises(
        ValueError,
        match="계약에 없는 문항",
    ):
        service.aggregate(
            [
                WrongEventObservation(
                    question_code=(
                        "unknown_question"
                    ),
                    wrong_event=1,
                ),
            ],
        )


def test_rejects_invalid_wrong_event_value(
    service: WrongEventAggregationService,
) -> None:
    with pytest.raises(
        ValueError,
        match="0, 1 또는",
    ):
        service.aggregate(
            [
                WrongEventObservation(
                    question_code=(
                        "orientation_year"
                    ),
                    wrong_event=2,
                ),
            ],
        )