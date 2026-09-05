from pathlib import Path

import pytest

from app.contracts.loader import (
    load_contract_bundle,
)
from app.core.config import PROJECT_ROOT
from app.scoring.response_delay import (
    ResponseDelayAggregationService,
    ResponseDelayObservation,
)


def create_service() -> (
    ResponseDelayAggregationService
):
    bundle = load_contract_bundle(
        PROJECT_ROOT / "contracts",
    )

    return (
        ResponseDelayAggregationService
        .from_contract_bundle(bundle)
    )


def test_aggregates_category_balanced_delay() -> None:
    service = create_service()

    result = service.aggregate(
        (
            ResponseDelayObservation(
                question_code="orientation_year",
                response_delay_ms=0,
            ),
            ResponseDelayObservation(
                question_code="orientation_month",
                response_delay_ms=1000,
            ),
            ResponseDelayObservation(
                question_code=(
                    "memory_registration_first"
                ),
                response_delay_ms=1000,
            ),
            ResponseDelayObservation(
                question_code=(
                    "memory_registration_second"
                ),
                response_delay_ms=3000,
            ),
            ResponseDelayObservation(
                question_code=(
                    "attention_digit_span_4"
                ),
                response_delay_ms=500,
            ),
            ResponseDelayObservation(
                question_code=(
                    "language_semantic_fluency"
                ),
                response_delay_ms=1000,
            ),
        ),
    )

    assert (
        result.category_median_delay_seconds
        == {
            "orientation": 0.5,
            "memory": 2.0,
            "attention": 0.5,
            "language": 1.0,
        }
    )
    assert (
        result.category_balanced_median_delay
        == 1.0
    )


def test_zero_delay_is_valid() -> None:
    service = create_service()

    result = service.aggregate(
        (
            ResponseDelayObservation(
                question_code="orientation_year",
                response_delay_ms=0,
            ),
            ResponseDelayObservation(
                question_code=(
                    "memory_registration_first"
                ),
                response_delay_ms=0,
            ),
            ResponseDelayObservation(
                question_code=(
                    "attention_digit_span_4"
                ),
                response_delay_ms=0,
            ),
            ResponseDelayObservation(
                question_code=(
                    "language_semantic_fluency"
                ),
                response_delay_ms=0,
            ),
        ),
    )

    assert (
        result.category_balanced_median_delay
        == 0.0
    )


def test_null_delay_is_excluded() -> None:
    service = create_service()

    result = service.aggregate(
        (
            ResponseDelayObservation(
                question_code=(
                    "orientation_place"
                ),
                response_delay_ms=None,
            ),
            ResponseDelayObservation(
                question_code="orientation_year",
                response_delay_ms=1000,
            ),
            ResponseDelayObservation(
                question_code=(
                    "memory_registration_first"
                ),
                response_delay_ms=2000,
            ),
            ResponseDelayObservation(
                question_code=(
                    "attention_digit_span_4"
                ),
                response_delay_ms=3000,
            ),
            ResponseDelayObservation(
                question_code=(
                    "language_semantic_fluency"
                ),
                response_delay_ms=4000,
            ),
        ),
    )

    assert (
        result.category_median_delay_seconds[
            "orientation"
        ]
        == 1.0
    )
    assert (
        result.category_balanced_median_delay
        == 2.5
    )


def test_even_number_of_delays_uses_median() -> None:
    service = create_service()

    result = service.aggregate(
        (
            ResponseDelayObservation(
                question_code="orientation_year",
                response_delay_ms=100,
            ),
            ResponseDelayObservation(
                question_code="orientation_month",
                response_delay_ms=300,
            ),
            ResponseDelayObservation(
                question_code=(
                    "memory_registration_first"
                ),
                response_delay_ms=1000,
            ),
            ResponseDelayObservation(
                question_code=(
                    "attention_digit_span_4"
                ),
                response_delay_ms=1000,
            ),
            ResponseDelayObservation(
                question_code=(
                    "language_semantic_fluency"
                ),
                response_delay_ms=1000,
            ),
        ),
    )

    assert (
        result.category_median_delay_seconds[
            "orientation"
        ]
        == 0.2
    )


def test_missing_core_category_is_rejected() -> None:
    service = create_service()

    with pytest.raises(
        ValueError,
        match="핵심 범주",
    ):
        service.aggregate(
            (
                ResponseDelayObservation(
                    question_code=(
                        "orientation_year"
                    ),
                    response_delay_ms=100,
                ),
                ResponseDelayObservation(
                    question_code=(
                        "memory_registration_first"
                    ),
                    response_delay_ms=200,
                ),
                ResponseDelayObservation(
                    question_code=(
                        "attention_digit_span_4"
                    ),
                    response_delay_ms=300,
                ),
            ),
        )


def test_duplicate_question_is_rejected() -> None:
    service = create_service()

    with pytest.raises(
        ValueError,
        match="중복",
    ):
        service.aggregate(
            (
                ResponseDelayObservation(
                    question_code=(
                        "orientation_year"
                    ),
                    response_delay_ms=100,
                ),
                ResponseDelayObservation(
                    question_code=(
                        "orientation_year"
                    ),
                    response_delay_ms=200,
                ),
            ),
        )


def test_negative_delay_is_rejected() -> None:
    service = create_service()

    with pytest.raises(
        ValueError,
        match="0 이상",
    ):
        service.aggregate(
            (
                ResponseDelayObservation(
                    question_code=(
                        "orientation_year"
                    ),
                    response_delay_ms=-1,
                ),
            ),
        )


def test_unknown_question_is_rejected() -> None:
    service = create_service()

    with pytest.raises(
        ValueError,
        match="사용 대상이 아닌",
    ):
        service.aggregate(
            (
                ResponseDelayObservation(
                    question_code=(
                        "unknown_question"
                    ),
                    response_delay_ms=100,
                ),
            ),
        )