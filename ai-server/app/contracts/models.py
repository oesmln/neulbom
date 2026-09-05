from typing import Literal

from pydantic import BaseModel, ConfigDict


class ContractModel(BaseModel):
    """기준 계약 공통 모델."""

    model_config = ConfigDict(
        extra="allow",
        frozen=True,
    )


class FeatureUsage(ContractModel):
    ast: bool
    kcelectra: bool
    response_delay: bool
    wrong_event: bool
    item_score: bool
    final_fusion: bool


class QuestionDefinition(ContractModel):
    legacy_question_id: str
    question_code: str
    variant_id: str
    order: int
    question_type: Literal[
        "orientation",
        "memory",
        "attention",
        "language",
    ]
    canonical_question: str
    response_mode: str
    administration_mode: Literal["always", "conditional"]
    scoring_mode: Literal["rule_based", "unscored_context"]
    feature_usage: FeatureUsage


class AdministrationDefinition(ContractModel):
    always_required_question_codes: list[str]
    conditional_question_codes: list[str]
    conditional_source_question_code: str


class MemoryStoryUnit(ContractModel):
    unit_code: str
    expected_answer: str
    recognition_question_code: str


class MemoryStoryDefinition(ContractModel):
    canonical_text: str
    units: list[MemoryStoryUnit]


class CistQuestionSet(ContractModel):
    schema_version: str
    question_set_version: str
    locale: str
    timezone: str
    scoring_rule_version: str
    normalization_rule_version: str
    core_categories: list[str]
    administration: AdministrationDefinition
    memory_story: MemoryStoryDefinition
    questions: list[QuestionDefinition]


class ObjectiveAnswerPolicy(ContractModel):
    applies_to_question_codes: list[str]


class ExplicitFailureEventPolicy(ContractModel):
    applies_to_question_codes: list[str]


class ExcludedQuestionPolicy(ContractModel):
    question_codes: list[str]


class AggregationPolicy(ContractModel):
    category_mapping: dict[str, str]
    required_category_count: int


class OperationalRuntimeContract(ContractModel):
    objective_answer_policy: ObjectiveAnswerPolicy
    explicit_failure_event_policy: ExplicitFailureEventPolicy
    excluded_question_policy: ExcludedQuestionPolicy
    aggregation: AggregationPolicy


class WrongEventRuleSet(ContractModel):
    schema_version: str
    wrong_event_rule_version: str
    question_set_version: str
    normalization_rule_version: str
    operational_runtime_contract: OperationalRuntimeContract


class ContractBundle(ContractModel):
    cist: CistQuestionSet
    wrong_event: WrongEventRuleSet