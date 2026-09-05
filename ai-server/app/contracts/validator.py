from collections import Counter
from collections.abc import Iterable

from app.contracts.models import (
    CistQuestionSet,
    ContractBundle,
    WrongEventRuleSet,
)

EXPECTED_CIST_SCHEMA_VERSION = "cist-question-set-schema-v1"
EXPECTED_QUESTION_SET_VERSION = "cist-v1"
EXPECTED_WRONG_EVENT_SCHEMA_VERSION = (
    "wrong-event-rule-schema-v1"
)
EXPECTED_WRONG_EVENT_RULE_VERSION = "wrong-event-v1"
EXPECTED_NORMALIZATION_RULE_VERSION = (
    "korean-answer-normalization-v1"
)

EXPECTED_CORE_CATEGORIES = {
    "orientation",
    "memory",
    "attention",
    "language",
}

# question_code, order, question_type, administration_mode, scoring_mode
EXPECTED_QUESTION_SPECS = {
    "Q01": (
        "orientation_year",
        1,
        "orientation",
        "always",
        "rule_based",
    ),
    "Q02": (
        "orientation_month",
        2,
        "orientation",
        "always",
        "rule_based",
    ),
    "Q03": (
        "orientation_day",
        3,
        "orientation",
        "always",
        "rule_based",
    ),
    "Q04": (
        "orientation_weekday",
        4,
        "orientation",
        "always",
        "rule_based",
    ),
    "Q05": (
        "orientation_place",
        5,
        "orientation",
        "always",
        "unscored_context",
    ),
    "Q06": (
        "memory_registration_first",
        6,
        "memory",
        "always",
        "unscored_context",
    ),
    "Q07": (
        "memory_registration_second",
        7,
        "memory",
        "always",
        "unscored_context",
    ),
    "Q08": (
        "attention_digit_span_4",
        8,
        "attention",
        "always",
        "rule_based",
    ),
    "Q09": (
        "attention_digit_span_5",
        9,
        "attention",
        "always",
        "rule_based",
    ),
    "Q10": (
        "attention_word_reverse",
        10,
        "attention",
        "always",
        "rule_based",
    ),
    "Q11": (
        "memory_delayed_free_recall",
        11,
        "memory",
        "always",
        "unscored_context",
    ),
    "Q12": (
        "memory_recognition_person",
        12,
        "memory",
        "conditional",
        "rule_based",
    ),
    "Q13": (
        "memory_recognition_transport",
        13,
        "memory",
        "conditional",
        "rule_based",
    ),
    "Q14": (
        "memory_recognition_place",
        14,
        "memory",
        "conditional",
        "rule_based",
    ),
    "Q15": (
        "memory_recognition_time",
        15,
        "memory",
        "conditional",
        "rule_based",
    ),
    "Q16": (
        "memory_recognition_activity",
        16,
        "memory",
        "conditional",
        "rule_based",
    ),
    "Q17": (
        "language_semantic_fluency",
        17,
        "language",
        "always",
        "unscored_context",
    ),
}

CONDITIONAL_LEGACY_IDS = {
    "Q12",
    "Q13",
    "Q14",
    "Q15",
    "Q16",
}

OBJECTIVE_LEGACY_IDS = {
    "Q01",
    "Q02",
    "Q03",
    "Q04",
    "Q08",
    "Q09",
    "Q10",
    "Q12",
    "Q13",
    "Q14",
    "Q15",
    "Q16",
}

EXPLICIT_FAILURE_LEGACY_IDS = {
    "Q06",
    "Q07",
    "Q11",
}

EXCLUDED_LEGACY_IDS = {
    "Q05",
    "Q17",
}

MEMORY_UNIT_LEGACY_IDS = {
    "person": "Q12",
    "transport": "Q13",
    "place": "Q14",
    "time": "Q15",
    "activity": "Q16",
}


class ContractValidationError(ValueError):
    """기준 계약의 내용이나 참조 관계가 잘못된 경우 발생한다."""


def validate_contract_bundle(
    bundle: ContractBundle,
) -> None:
    """두 계약의 버전과 문항 참조 관계를 검증한다."""
    _validate_versions(
        bundle.cist,
        bundle.wrong_event,
    )
    _validate_questions(bundle.cist)
    _validate_administration(bundle.cist)
    _validate_memory_story(bundle.cist)
    _validate_wrong_event_policies(
        bundle.cist,
        bundle.wrong_event,
    )


def _validate_versions(
    cist: CistQuestionSet,
    wrong_event: WrongEventRuleSet,
) -> None:
    _require_equal(
        "cist.schema_version",
        cist.schema_version,
        EXPECTED_CIST_SCHEMA_VERSION,
    )
    _require_equal(
        "cist.question_set_version",
        cist.question_set_version,
        EXPECTED_QUESTION_SET_VERSION,
    )
    _require_equal(
        "wrong_event.schema_version",
        wrong_event.schema_version,
        EXPECTED_WRONG_EVENT_SCHEMA_VERSION,
    )
    _require_equal(
        "wrong_event.wrong_event_rule_version",
        wrong_event.wrong_event_rule_version,
        EXPECTED_WRONG_EVENT_RULE_VERSION,
    )
    _require_equal(
        "wrong_event.question_set_version",
        wrong_event.question_set_version,
        cist.question_set_version,
    )
    _require_equal(
        "cist.scoring_rule_version",
        cist.scoring_rule_version,
        wrong_event.wrong_event_rule_version,
    )
    _require_equal(
        "cist.normalization_rule_version",
        cist.normalization_rule_version,
        EXPECTED_NORMALIZATION_RULE_VERSION,
    )
    _require_equal(
        "wrong_event.normalization_rule_version",
        wrong_event.normalization_rule_version,
        cist.normalization_rule_version,
    )


def _validate_questions(
    cist: CistQuestionSet,
) -> None:
    _require(
        len(cist.questions) == 17,
        "questions는 정확히 17개여야 합니다: "
        f"{len(cist.questions)}개",
    )

    legacy_ids = [
        question.legacy_question_id
        for question in cist.questions
    ]
    question_codes = [
        question.question_code
        for question in cist.questions
    ]
    orders = [
        question.order
        for question in cist.questions
    ]

    _require_unique(
        "legacy_question_id",
        legacy_ids,
    )
    _require_unique(
        "question_code",
        question_codes,
    )
    _require_unique(
        "order",
        orders,
    )
    _require_exact_set(
        "legacy_question_id",
        legacy_ids,
        EXPECTED_QUESTION_SPECS,
    )
    _require_exact_set(
        "order",
        orders,
        range(1, 18),
    )
    _require_exact_set(
        "core_categories",
        cist.core_categories,
        EXPECTED_CORE_CATEGORIES,
    )

    for question in cist.questions:
        expected = EXPECTED_QUESTION_SPECS[
            question.legacy_question_id
        ]
        (
            expected_code,
            expected_order,
            expected_type,
            expected_administration,
            expected_scoring,
        ) = expected

        _require_equal(
            f"{question.legacy_question_id}.question_code",
            question.question_code,
            expected_code,
        )
        _require_equal(
            f"{question.legacy_question_id}.order",
            question.order,
            expected_order,
        )
        _require_equal(
            f"{question.legacy_question_id}.question_type",
            question.question_type,
            expected_type,
        )
        _require_equal(
            f"{question.legacy_question_id}.administration_mode",
            question.administration_mode,
            expected_administration,
        )
        _require_equal(
            f"{question.legacy_question_id}.scoring_mode",
            question.scoring_mode,
            expected_scoring,
        )


def _validate_administration(
    cist: CistQuestionSet,
) -> None:
    always_codes = (
        cist.administration.always_required_question_codes
    )
    conditional_codes = (
        cist.administration.conditional_question_codes
    )

    _require_unique(
        "always_required_question_codes",
        always_codes,
    )
    _require_unique(
        "conditional_question_codes",
        conditional_codes,
    )

    expected_always = {
        question.question_code
        for question in cist.questions
        if question.administration_mode == "always"
    }
    expected_conditional = {
        question.question_code
        for question in cist.questions
        if question.administration_mode == "conditional"
    }

    _require_exact_set(
        "always_required_question_codes",
        always_codes,
        expected_always,
    )
    _require_exact_set(
        "conditional_question_codes",
        conditional_codes,
        expected_conditional,
    )

    by_legacy_id = {
        question.legacy_question_id: question
        for question in cist.questions
    }

    expected_q12_to_q16 = {
        by_legacy_id[legacy_id].question_code
        for legacy_id in CONDITIONAL_LEGACY_IDS
    }
    _require_exact_set(
        "Q12~Q16 조건부 시행 문항",
        conditional_codes,
        expected_q12_to_q16,
    )
    _require_equal(
        "conditional_source_question_code",
        cist.administration.conditional_source_question_code,
        by_legacy_id["Q11"].question_code,
    )


def _validate_memory_story(
    cist: CistQuestionSet,
) -> None:
    units = cist.memory_story.units
    unit_codes = [
        unit.unit_code
        for unit in units
    ]

    _require_unique(
        "memory_story.units.unit_code",
        unit_codes,
    )
    _require_exact_set(
        "memory_story.units.unit_code",
        unit_codes,
        MEMORY_UNIT_LEGACY_IDS,
    )

    by_legacy_id = {
        question.legacy_question_id: question
        for question in cist.questions
    }

    recognition_codes = set()

    for unit in units:
        expected_legacy_id = MEMORY_UNIT_LEGACY_IDS[
            unit.unit_code
        ]
        expected_question_code = by_legacy_id[
            expected_legacy_id
        ].question_code

        recognition_codes.add(expected_question_code)

        _require_equal(
            "memory_story "
            f"unit={unit.unit_code} recognition_question_code",
            unit.recognition_question_code,
            expected_question_code,
        )

    _require_exact_set(
        "memory_story recognition question_code",
        recognition_codes,
        cist.administration.conditional_question_codes,
    )


def _validate_wrong_event_policies(
    cist: CistQuestionSet,
    wrong_event: WrongEventRuleSet,
) -> None:
    runtime = wrong_event.operational_runtime_contract

    objective_codes = (
        runtime
        .objective_answer_policy
        .applies_to_question_codes
    )
    explicit_failure_codes = (
        runtime
        .explicit_failure_event_policy
        .applies_to_question_codes
    )
    excluded_codes = (
        runtime
        .excluded_question_policy
        .question_codes
    )

    policy_groups = {
        "objective_answer_policy": objective_codes,
        "explicit_failure_event_policy": explicit_failure_codes,
        "excluded_question_policy": excluded_codes,
    }

    all_question_codes = {
        question.question_code
        for question in cist.questions
    }

    for name, codes in policy_groups.items():
        _require_unique(name, codes)

        unknown_codes = set(codes) - all_question_codes
        _require(
            not unknown_codes,
            f"{name}가 존재하지 않는 question_code를 "
            f"참조합니다: {sorted(unknown_codes)}",
        )

    combined_codes = [
        code
        for codes in policy_groups.values()
        for code in codes
    ]

    _require_unique(
        "wrong-event 문항 정책 전체",
        combined_codes,
    )
    _require_exact_set(
        "wrong-event 문항 정책 전체",
        combined_codes,
        all_question_codes,
    )

    by_legacy_id = {
        question.legacy_question_id: question
        for question in cist.questions
    }

    expected_objective = {
        by_legacy_id[legacy_id].question_code
        for legacy_id in OBJECTIVE_LEGACY_IDS
    }
    expected_explicit_failure = {
        by_legacy_id[legacy_id].question_code
        for legacy_id in EXPLICIT_FAILURE_LEGACY_IDS
    }
    expected_excluded = {
        by_legacy_id[legacy_id].question_code
        for legacy_id in EXCLUDED_LEGACY_IDS
    }

    _require_exact_set(
        "objective_answer_policy",
        objective_codes,
        expected_objective,
    )
    _require_exact_set(
        "explicit_failure_event_policy",
        explicit_failure_codes,
        expected_explicit_failure,
    )
    _require_exact_set(
        "excluded_question_policy",
        excluded_codes,
        expected_excluded,
    )

    feature_excluded = {
        question.question_code
        for question in cist.questions
        if not question.feature_usage.wrong_event
    }

    _require_exact_set(
        "feature_usage.wrong_event=false 문항",
        feature_excluded,
        expected_excluded,
    )

    expected_category_mapping = {
        category: category
        for category in EXPECTED_CORE_CATEGORIES
    }
    aggregation = runtime.aggregation

    _require_equal(
        "aggregation.category_mapping",
        aggregation.category_mapping,
        expected_category_mapping,
    )
    _require_equal(
        "aggregation.required_category_count",
        aggregation.required_category_count,
        len(EXPECTED_CORE_CATEGORIES),
    )


def _require(
    condition: bool,
    message: str,
) -> None:
    if not condition:
        raise ContractValidationError(message)


def _require_equal(
    name: str,
    actual: object,
    expected: object,
) -> None:
    _require(
        actual == expected,
        f"{name}가 일치하지 않습니다: "
        f"actual={actual!r}, expected={expected!r}",
    )


def _require_unique(
    name: str,
    values: Iterable[object],
) -> None:
    value_list = list(values)
    duplicates = sorted(
        (
            value
            for value, count
            in Counter(value_list).items()
            if count > 1
        ),
        key=str,
    )

    _require(
        not duplicates,
        f"{name}에 중복값이 있습니다: {duplicates}",
    )


def _require_exact_set(
    name: str,
    actual: Iterable[object],
    expected: Iterable[object],
) -> None:
    actual_set = set(actual)
    expected_set = set(expected)

    missing = sorted(
        expected_set - actual_set,
        key=str,
    )
    unexpected = sorted(
        actual_set - expected_set,
        key=str,
    )

    _require(
        not missing and not unexpected,
        f"{name} 구성이 일치하지 않습니다: "
        f"missing={missing}, unexpected={unexpected}",
    )