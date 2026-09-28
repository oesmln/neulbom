from datetime import date, datetime
from math import isclose, isfinite
from typing import (
    Annotated,
    Literal,
    get_args,
)
from uuid import UUID

from pydantic import (
    Field,
    field_validator,
    model_validator,
)

from app.api.schemas.common import (
    APIModel,
    AudioResource,
    ConditionalQuestionCode,
    MemoryUnitMap,
    QuestionAnalysisResult,
    QuestionCode,
    QuestionSetVersion,
    ResponseTiming,
    RetryReasonCode,
    SttConfig,
    SttInput,
    Timezone,
    WrongEventRuleVersion,
)
from app.inference.risk_policy import (
    RiskLevel,
)

AnalysisStatus = Literal[
    "pending",
    "processing",
    "needs_retry",
    "completed",
    "failed",
]

RetryAction = Literal[
    "REISSUE_AUDIO_URL",
    "REPLACE_RESPONSE",
]

AnalysisReasonCode = (
    RetryReasonCode
    | Literal[
        "MODEL_UNAVAILABLE",
        "INTERNAL_ERROR",
    ]
)

EXPECTED_QUESTION_CODES = frozenset(
    get_args(QuestionCode),
)

CoreCistCategory = Literal[
    "orientation",
    "memory",
    "attention",
    "language",
]

EXPECTED_CORE_CATEGORIES = frozenset(
    get_args(CoreCistCategory),
)

EXPECTED_QUESTION_CATEGORIES = {
    "orientation_year": "orientation",
    "orientation_month": "orientation",
    "orientation_day": "orientation",
    "orientation_weekday": "orientation",
    "orientation_place": "orientation",
    "memory_registration_first": "memory",
    "memory_registration_second": "memory",
    "attention_digit_span_4": "attention",
    "attention_digit_span_5": "attention",
    "attention_word_reverse": "attention",
    "memory_delayed_free_recall": "memory",
    "memory_recognition_person": "memory",
    "memory_recognition_transport": "memory",
    "memory_recognition_place": "memory",
    "memory_recognition_time": "memory",
    "memory_recognition_activity": "memory",
    "language_semantic_fluency": "language",
}

FeatureSnapshotSchemaVersion = Literal[
    "cognitive-feature-snapshot-v1"
]

DailyQuestionCode = Literal[
    "orientation_year",
    "orientation_month",
    "orientation_day",
    "orientation_weekday",
    "orientation_place",
    "attention_digit_span_4",
    "attention_digit_span_5",
    "attention_word_reverse",
]

DAILY_ORIENTATION_CODES = frozenset(
    {
        "orientation_year",
        "orientation_month",
        "orientation_day",
        "orientation_weekday",
        "orientation_place",
    },
)

DAILY_ATTENTION_CODES = frozenset(
    {
        "attention_digit_span_4",
        "attention_digit_span_5",
        "attention_word_reverse",
    },
)


class AdministeredQuestionResponse(APIModel):
    question_code: QuestionCode
    variant_id: str = Field(min_length=1)
    administration_status: Literal[
        "administered"
    ]
    recording_id: UUID
    response_id: UUID
    audio: AudioResource
    stt: SttInput
    timing: ResponseTiming


class NotApplicableQuestionResponse(APIModel):
    question_code: ConditionalQuestionCode
    variant_id: str = Field(min_length=1)
    administration_status: Literal[
        "not_applicable"
    ]


QuestionResponseInput = Annotated[
    AdministeredQuestionResponse
    | NotApplicableQuestionResponse,
    Field(
        discriminator="administration_status",
    ),
]


class RecognitionPlanSnapshot(APIModel):
    source_question_code: Literal[
        "memory_delayed_free_recall"
    ]
    recalled_units: MemoryUnitMap
    selected_question_codes: list[
        ConditionalQuestionCode
    ] = Field(
        min_length=0,
        max_length=5,
    )

    @field_validator(
        "selected_question_codes",
    )
    @classmethod
    def reject_duplicate_codes(
        cls,
        value: list[
            ConditionalQuestionCode
        ],
    ) -> list[ConditionalQuestionCode]:
        if len(value) != len(set(value)):
            raise ValueError(
                "selected_question_codes에는 "
                "중복값이 있을 수 없습니다.",
            )

        return value


class AnalysisCreateRequest(APIModel):
    analysis_id: UUID
    assessment_id: UUID
    question_set_version: QuestionSetVersion
    wrong_event_rule_version: (
        WrongEventRuleVersion
    )
    assessment_local_date: date
    timezone: Timezone
    stt_config: SttConfig
    recognition_plan: RecognitionPlanSnapshot
    responses: list[
        QuestionResponseInput
    ] = Field(
        min_length=17,
        max_length=17,
    )


class AnalysisAcceptedResponse(APIModel):
    analysis_id: UUID
    assessment_id: UUID
    status: Literal[
        "pending",
        "processing",
    ]
    created_at: datetime
    
class ReissueAudioUrlItem(APIModel):
    question_code: QuestionCode
    retry_action: Literal[
        "REISSUE_AUDIO_URL"
    ]
    recording_id: UUID
    response_id: UUID
    audio: AudioResource


class ReplaceResponseItem(APIModel):
    question_code: QuestionCode
    retry_action: Literal[
        "REPLACE_RESPONSE"
    ]
    variant_id: str = Field(
        min_length=1,
    )
    recording_id: UUID
    response_id: UUID
    audio: AudioResource
    stt: SttInput
    timing: ResponseTiming


AnalysisRetryItem = Annotated[
    ReissueAudioUrlItem
    | ReplaceResponseItem,
    Field(
        discriminator="retry_action",
    ),
]


class AnalysisRetryRequest(APIModel):
    reason_code: RetryReasonCode
    items: list[
        AnalysisRetryItem
    ] = Field(
        min_length=1,
        max_length=17,
    )

    @model_validator(mode="after")
    def validate_retry_items(
        self,
    ) -> "AnalysisRetryRequest":
        question_codes = [
            item.question_code
            for item in self.items
        ]

        if len(question_codes) != len(
            set(question_codes),
        ):
            raise ValueError(
                "재시도 문항 코드가 "
                "중복되었습니다.",
            )

        recording_ids = [
            item.recording_id
            for item in self.items
        ]

        if len(recording_ids) != len(
            set(recording_ids),
        ):
            raise ValueError(
                "재시도 recording_id가 "
                "중복되었습니다.",
            )

        response_ids = [
            item.response_id
            for item in self.items
        ]

        if len(response_ids) != len(
            set(response_ids),
        ):
            raise ValueError(
                "재시도 response_id가 "
                "중복되었습니다.",
            )

        return self


class RetryItem(APIModel):
    question_code: QuestionCode
    reason_code: RetryReasonCode
    required_action: RetryAction


class FusionFeatureValues(APIModel):
    ast_logit: float
    kcelectra_logit: float
    category_balanced_wrong_event_score: (
        float
    ) = Field(
        ge=0,
        le=1,
    )
    category_balanced_median_delay: float = (
        Field(ge=0)
    )

    @field_validator(
        "ast_logit",
        "kcelectra_logit",
        "category_balanced_wrong_event_score",
        "category_balanced_median_delay",
    )
    @classmethod
    def require_finite_values(
        cls,
        value: float,
    ) -> float:
        if not isfinite(value):
            raise ValueError(
                "fusion feature는 유한한 "
                "숫자여야 합니다.",
            )

        return value


class AstQuestionFeatureSnapshot(APIModel):
    question_code: QuestionCode
    category: CoreCistCategory
    dementia_logit: float
    segment_count: int = Field(ge=1)

    @field_validator("dementia_logit")
    @classmethod
    def require_finite_logit(
        cls,
        value: float,
    ) -> float:
        if not isfinite(value):
            raise ValueError(
                "AST 문항 logit은 유한한 "
                "숫자여야 합니다.",
            )

        return value


class KcElectraQuestionFeatureSnapshot(
    APIModel,
):
    question_code: QuestionCode
    category: CoreCistCategory
    dementia_logit: float

    @field_validator("dementia_logit")
    @classmethod
    def require_finite_logit(
        cls,
        value: float,
    ) -> float:
        if not isfinite(value):
            raise ValueError(
                "KcELECTRA 문항 logit은 "
                "유한한 숫자여야 합니다.",
            )

        return value


class WrongEventFeatureObservation(APIModel):
    question_code: QuestionCode
    wrong_event: Literal[0, 1] | None


class ResponseDelayFeatureObservation(APIModel):
    question_code: QuestionCode
    response_delay_ms: int | None = Field(
        ge=0,
    )


class CognitiveFeatureSnapshot(APIModel):
    schema_version: FeatureSnapshotSchemaVersion
    question_set_version: QuestionSetVersion
    wrong_event_rule_version: (
        WrongEventRuleVersion
    )
    ast_model_version: Literal[
        "final_ast_core4_epoch6_3seed_ensemble"
    ]
    kcelectra_model_version: Literal[
        "final_kcelectra_service_"
        "352clips_seed_ensemble_v1"
    ]
    fusion_model_version: Literal[
        "final_fusion_lr_"
        "21subjects_ast20_mean_logit_3seed_v2"
    ]
    threshold_version: Literal[
        "fusion-threshold-v2"
    ]
    model_score: float = Field(
        ge=0,
        le=1,
    )
    ast_question_features: list[
        AstQuestionFeatureSnapshot
    ] = Field(
        min_length=1,
        max_length=17,
    )
    kcelectra_question_features: list[
        KcElectraQuestionFeatureSnapshot
    ] = Field(
        min_length=1,
        max_length=17,
    )
    wrong_event_observations: list[
        WrongEventFeatureObservation
    ] = Field(
        min_length=17,
        max_length=17,
    )
    response_delay_observations: list[
        ResponseDelayFeatureObservation
    ] = Field(
        min_length=17,
        max_length=17,
    )
    fusion_features: FusionFeatureValues

    @field_validator("model_score")
    @classmethod
    def require_finite_model_score(
        cls,
        value: float,
    ) -> float:
        if not isfinite(value):
            raise ValueError(
                "스냅샷 model_score는 유한한 "
                "숫자여야 합니다.",
            )

        return value

    @model_validator(mode="after")
    def validate_feature_sets(
        self,
    ) -> "CognitiveFeatureSnapshot":
        ast_codes = self._unique_codes(
            self.ast_question_features,
            "AST",
        )
        kcelectra_codes = self._unique_codes(
            self.kcelectra_question_features,
            "KcELECTRA",
        )

        if ast_codes != kcelectra_codes:
            raise ValueError(
                "AST와 KcELECTRA 스냅샷의 "
                "문항 코드가 일치해야 합니다.",
            )

        for label, features in (
            (
                "AST",
                self.ast_question_features,
            ),
            (
                "KcELECTRA",
                self.kcelectra_question_features,
            ),
        ):
            categories = {
                feature.category
                for feature in features
            }

            if categories != EXPECTED_CORE_CATEGORIES:
                raise ValueError(
                    f"{label} 스냅샷은 Core4 범주를 "
                    "모두 포함해야 합니다.",
                )

            mismatched_codes = [
                feature.question_code
                for feature in features
                if (
                    EXPECTED_QUESTION_CATEGORIES[
                        feature.question_code
                    ]
                    != feature.category
                )
            ]

            if mismatched_codes:
                raise ValueError(
                    f"{label} 스냅샷의 문항 범주가 "
                    "cist-v1과 일치하지 않습니다: "
                    f"{sorted(mismatched_codes)}",
                )

        for label, observations in (
            (
                "wrong_event",
                self.wrong_event_observations,
            ),
            (
                "response_delay",
                self.response_delay_observations,
            ),
        ):
            codes = self._unique_codes(
                observations,
                label,
            )

            if codes != EXPECTED_QUESTION_CODES:
                raise ValueError(
                    f"{label} 스냅샷은 정확한 "
                    "17개 문항을 포함해야 합니다.",
                )

        return self

    @staticmethod
    def _unique_codes(
        values: list,
        label: str,
    ) -> set[str]:
        codes = [
            value.question_code
            for value in values
        ]

        if len(codes) != len(set(codes)):
            raise ValueError(
                f"{label} 스냅샷의 문항 코드가 "
                "중복되었습니다.",
            )

        return set(codes)


class DailyAnalysisCreateRequest(APIModel):
    analysis_type: Literal[
        "daily_partial_update"
    ]
    analysis_id: UUID
    session_id: UUID
    baseline_analysis_id: UUID
    question_set_version: QuestionSetVersion
    wrong_event_rule_version: (
        WrongEventRuleVersion
    )
    assessment_local_date: date
    timezone: Timezone
    stt_config: SttConfig
    baseline_model_score: float = Field(
        ge=0,
        le=1,
    )
    input_snapshot: CognitiveFeatureSnapshot
    responses: list[
        AdministeredQuestionResponse
    ] = Field(
        min_length=2,
        max_length=2,
    )

    @field_validator("baseline_model_score")
    @classmethod
    def require_finite_baseline_score(
        cls,
        value: float,
    ) -> float:
        if not isfinite(value):
            raise ValueError(
                "baseline_model_score는 유한한 "
                "숫자여야 합니다.",
            )

        return value

    @model_validator(mode="after")
    def validate_daily_request(
        self,
    ) -> "DailyAnalysisCreateRequest":
        response_codes = [
            response.question_code
            for response in self.responses
        ]

        if len(response_codes) != len(
            set(response_codes),
        ):
            raise ValueError(
                "일상 CIST 문항 코드가 "
                "중복되었습니다.",
            )

        code_set = set(response_codes)

        if (
            len(
                code_set
                & DAILY_ORIENTATION_CODES
            )
            != 1
            or len(
                code_set
                & DAILY_ATTENTION_CODES
            )
            != 1
            or len(code_set) != 2
        ):
            raise ValueError(
                "일상 CIST 분석은 지남력 1문항과 "
                "주의력 1문항으로 구성해야 합니다.",
            )

        snapshot = self.input_snapshot

        if (
            snapshot.question_set_version
            != self.question_set_version
            or snapshot.wrong_event_rule_version
            != self.wrong_event_rule_version
        ):
            raise ValueError(
                "입력 스냅샷의 계약 버전이 일상 "
                "분석 요청과 일치하지 않습니다.",
            )

        ast_codes = {
            feature.question_code
            for feature
            in snapshot.ast_question_features
        }
        kcelectra_codes = {
            feature.question_code
            for feature
            in snapshot.kcelectra_question_features
        }

        if (
            not code_set <= ast_codes
            or not code_set <= kcelectra_codes
        ):
            raise ValueError(
                "일상 분석 문항이 입력 스냅샷의 "
                "모델 특징에 존재하지 않습니다.",
            )

        return self


class DailyAnalysisAcceptedResponse(APIModel):
    analysis_id: UUID
    session_id: UUID
    status: Literal[
        "pending",
        "processing",
    ]
    created_at: datetime


class DailyAnalysisResult(APIModel):
    result_type: Literal[
        "daily_partial_estimate"
    ]
    baseline_analysis_id: UUID
    baseline_model_score: float = Field(
        ge=0,
        le=1,
    )
    input_model_score: float = Field(
        ge=0,
        le=1,
    )
    estimated_model_score: float = Field(
        ge=0,
        le=1,
    )
    score_delta_from_baseline: float
    score_delta_from_previous: float
    model_version: Literal[
        "final_fusion_lr_"
        "21subjects_ast20_mean_logit_3seed_v2"
    ]
    decision_threshold: Literal[
        0.38592870327757767
    ]
    review_threshold: Literal[
        0.8061380697921943
    ]
    threshold_version: Literal[
        "fusion-threshold-v2"
    ]
    risk_flag: bool
    risk_level: RiskLevel
    updated_question_codes: list[
        DailyQuestionCode
    ] = Field(
        min_length=2,
        max_length=2,
    )
    features: FusionFeatureValues
    output_snapshot: CognitiveFeatureSnapshot
    question_results: list[
        QuestionAnalysisResult
    ] = Field(
        min_length=2,
        max_length=2,
    )

    @field_validator(
        "baseline_model_score",
        "input_model_score",
        "estimated_model_score",
        "score_delta_from_baseline",
        "score_delta_from_previous",
    )
    @classmethod
    def require_finite_scores(
        cls,
        value: float,
    ) -> float:
        if not isfinite(value):
            raise ValueError(
                "일상 인지 추이 점수는 유한한 "
                "숫자여야 합니다.",
            )

        return value

    @model_validator(mode="after")
    def validate_daily_result(
        self,
    ) -> "DailyAnalysisResult":
        updated_codes = set(
            self.updated_question_codes,
        )
        result_codes = {
            result.question_code
            for result in self.question_results
        }

        if (
            len(updated_codes) != 2
            or updated_codes != result_codes
        ):
            raise ValueError(
                "갱신 문항과 문항별 분석 결과가 "
                "일치해야 합니다.",
            )

        if not isclose(
            self.score_delta_from_baseline,
            self.estimated_model_score
            - self.baseline_model_score,
            abs_tol=1e-12,
        ):
            raise ValueError(
                "기준 점수 대비 변화량이 "
                "일치하지 않습니다.",
            )

        if not isclose(
            self.score_delta_from_previous,
            self.estimated_model_score
            - self.input_model_score,
            abs_tol=1e-12,
        ):
            raise ValueError(
                "직전 점수 대비 변화량이 "
                "일치하지 않습니다.",
            )

        expected_risk_flag = (
            self.estimated_model_score
            >= self.decision_threshold
        )

        if self.risk_flag != expected_risk_flag:
            raise ValueError(
                "risk_flag가 일상 추정 점수와 "
                "일치하지 않습니다.",
            )

        if (
            self.estimated_model_score
            < self.decision_threshold
        ):
            expected_risk_level = (
                RiskLevel.STABLE
            )
        elif (
            self.estimated_model_score
            < self.review_threshold
        ):
            expected_risk_level = (
                RiskLevel.MONITORING_NEEDED
            )
        else:
            expected_risk_level = (
                RiskLevel.REVIEW_NEEDED
            )

        if self.risk_level != expected_risk_level:
            raise ValueError(
                "risk_level이 일상 추정 점수와 "
                "일치하지 않습니다.",
            )

        snapshot = self.output_snapshot

        if (
            snapshot.model_score
            != self.estimated_model_score
            or snapshot.fusion_model_version
            != self.model_version
            or snapshot.threshold_version
            != self.threshold_version
            or snapshot.fusion_features
            != self.features
        ):
            raise ValueError(
                "output_snapshot이 일상 분석 "
                "결과와 일치하지 않습니다.",
            )

        return self


class DailyAnalysisStatusResponse(APIModel):
    analysis_id: UUID
    session_id: UUID
    status: AnalysisStatus
    created_at: datetime
    updated_at: datetime
    retryable: bool
    reason_code: AnalysisReasonCode | None
    retry_items: list[RetryItem] = Field(
        max_length=2,
    )
    result: DailyAnalysisResult | None

    @field_validator(
        "created_at",
        "updated_at",
    )
    @classmethod
    def require_timezone(
        cls,
        value: datetime,
    ) -> datetime:
        if (
            value.tzinfo is None
            or value.utcoffset() is None
        ):
            raise ValueError(
                "일상 분석 상태 시각에는 "
                "시간대가 필요합니다.",
            )

        return value

    @model_validator(mode="after")
    def validate_status_fields(
        self,
    ) -> "DailyAnalysisStatusResponse":
        if self.updated_at < self.created_at:
            raise ValueError(
                "updated_at은 created_at보다 "
                "빠를 수 없습니다.",
            )

        if self.status in {
            "pending",
            "processing",
        }:
            valid = (
                not self.retryable
                and self.reason_code is None
                and not self.retry_items
                and self.result is None
            )
        elif self.status == "needs_retry":
            valid = (
                self.retryable
                and self.reason_code
                in {
                    "INCOMPLETE_ASSESSMENT",
                    "UNSCORABLE_STT",
                    "AUDIO_URL_EXPIRED",
                    "AUDIO_DOWNLOAD_FAILED",
                    "UNSUPPORTED_AUDIO_FORMAT",
                }
                and bool(self.retry_items)
                and self.result is None
            )
        elif self.status == "completed":
            valid = (
                not self.retryable
                and self.reason_code is None
                and not self.retry_items
                and self.result is not None
            )
        elif self.status == "failed":
            valid = (
                not self.retryable
                and self.reason_code
                in {
                    "MODEL_UNAVAILABLE",
                    "INTERNAL_ERROR",
                }
                and not self.retry_items
                and self.result is None
            )
        else:
            valid = False

        if not valid:
            raise ValueError(
                "일상 분석 상태별 필드 구성이 "
                "올바르지 않습니다.",
            )

        return self


class FinalAnalysisResult(APIModel):
    question_set_version: QuestionSetVersion
    wrong_event_rule_version: (
        WrongEventRuleVersion
    )
    model_version: Literal[
        "final_fusion_lr_"
        "21subjects_ast20_mean_logit_3seed_v2"
    ]
    model_score: float = Field(
        ge=0,
        le=1,
    )
    decision_threshold: Literal[
        0.38592870327757767
    ]
    review_threshold: Literal[
        0.8061380697921943
    ]
    threshold_version: Literal[
        "fusion-threshold-v2"
    ]
    risk_flag: bool
    risk_level: RiskLevel
    features: FusionFeatureValues
    feature_snapshot: CognitiveFeatureSnapshot
    question_results: list[
        QuestionAnalysisResult
    ] = Field(
        min_length=17,
        max_length=17,
    )

    @field_validator("model_score")
    @classmethod
    def require_finite_model_score(
        cls,
        value: float,
    ) -> float:
        if not isfinite(value):
            raise ValueError(
                "model_score는 유한한 "
                "숫자여야 합니다.",
            )

        return value

    @model_validator(mode="after")
    def validate_final_result(
        self,
    ) -> "FinalAnalysisResult":
        question_codes = [
            result.question_code
            for result in self.question_results
        ]

        if len(question_codes) != len(
            set(question_codes),
        ):
            raise ValueError(
                "question_results의 문항 코드가 "
                "중복되었습니다.",
            )

        actual_codes = set(question_codes)

        if actual_codes != (
            EXPECTED_QUESTION_CODES
        ):
            missing_codes = sorted(
                EXPECTED_QUESTION_CODES
                - actual_codes
            )
            unexpected_codes = sorted(
                actual_codes
                - EXPECTED_QUESTION_CODES
            )

            raise ValueError(
                "question_results가 정확한 "
                "17개 문항으로 구성되지 않았습니다: "
                f"missing={missing_codes}, "
                f"unexpected={unexpected_codes}",
            )

        expected_risk_flag = (
            self.model_score
            >= self.decision_threshold
        )

        if self.risk_flag != expected_risk_flag:
            raise ValueError(
                "risk_flag가 model_score와 "
                "decision_threshold의 비교 결과와 "
                "일치하지 않습니다.",
            )

        if (
            self.model_score
            < self.decision_threshold
        ):
            expected_risk_level = (
                RiskLevel.STABLE
            )
        elif (
            self.model_score
            < self.review_threshold
        ):
            expected_risk_level = (
                RiskLevel.MONITORING_NEEDED
            )
        else:
            expected_risk_level = (
                RiskLevel.REVIEW_NEEDED
            )

        if self.risk_level != expected_risk_level:
            raise ValueError(
                "risk_level이 model_score와 "
                "두 운영 threshold의 비교 결과와 "
                "일치하지 않습니다.",
            )

        snapshot = self.feature_snapshot

        if (
            snapshot.question_set_version
            != self.question_set_version
            or snapshot.wrong_event_rule_version
            != self.wrong_event_rule_version
            or snapshot.fusion_model_version
            != self.model_version
            or snapshot.threshold_version
            != self.threshold_version
        ):
            raise ValueError(
                "feature_snapshot의 계약 및 모델 "
                "버전이 최종 결과와 일치하지 "
                "않습니다.",
            )

        if (
            snapshot.model_score
            != self.model_score
            or snapshot.fusion_features
            != self.features
        ):
            raise ValueError(
                "feature_snapshot의 Fusion 결과가 "
                "최종 결과와 일치하지 않습니다.",
            )

        administered_codes = {
            result.question_code
            for result in self.question_results
            if (
                result.administration_status
                == "administered"
            )
        }
        ast_codes = {
            feature.question_code
            for feature
            in snapshot.ast_question_features
        }
        kcelectra_codes = {
            feature.question_code
            for feature
            in snapshot.kcelectra_question_features
        }

        if (
            ast_codes != administered_codes
            or kcelectra_codes
            != administered_codes
        ):
            raise ValueError(
                "feature_snapshot의 모델 문항이 "
                "시행 문항과 일치하지 않습니다.",
            )

        return self


class AnalysisStatusResponse(APIModel):
    analysis_id: UUID
    assessment_id: UUID
    status: AnalysisStatus
    created_at: datetime
    updated_at: datetime
    retryable: bool
    reason_code: AnalysisReasonCode | None
    retry_items: list[RetryItem] = Field(
        max_length=17,
    )
    result: FinalAnalysisResult | None

    @field_validator(
        "created_at",
        "updated_at",
    )
    @classmethod
    def require_timezone(
        cls,
        value: datetime,
    ) -> datetime:
        if (
            value.tzinfo is None
            or value.utcoffset() is None
        ):
            raise ValueError(
                "분석 상태 시각에는 시간대가 "
                "필요합니다.",
            )

        return value

    @model_validator(mode="after")
    def validate_status_fields(
        self,
    ) -> "AnalysisStatusResponse":
        if self.updated_at < self.created_at:
            raise ValueError(
                "updated_at은 created_at보다 "
                "빠를 수 없습니다.",
            )

        if self.status in {
            "pending",
            "processing",
        }:
            if (
                self.retryable
                or self.reason_code is not None
                or self.retry_items
                or self.result is not None
            ):
                raise ValueError(
                    "대기 또는 처리 중 상태에는 "
                    "재시도 정보와 결과가 없어야 합니다.",
                )

            return self

        if self.status == "needs_retry":
            if not self.retryable:
                raise ValueError(
                    "needs_retry 상태는 "
                    "retryable=true여야 합니다.",
                )

            if self.reason_code not in {
                "INCOMPLETE_ASSESSMENT",
                "UNSCORABLE_STT",
                "AUDIO_URL_EXPIRED",
                "AUDIO_DOWNLOAD_FAILED",
                "UNSUPPORTED_AUDIO_FORMAT",
            }:
                raise ValueError(
                    "needs_retry 상태의 "
                    "reason_code가 올바르지 않습니다.",
                )

            if not self.retry_items:
                raise ValueError(
                    "needs_retry 상태에는 "
                    "retry_items가 필요합니다.",
                )

            if self.result is not None:
                raise ValueError(
                    "needs_retry 상태에는 "
                    "최종 결과가 없어야 합니다.",
                )

            return self

        if self.status == "completed":
            if (
                self.retryable
                or self.reason_code is not None
                or self.retry_items
                or self.result is None
            ):
                raise ValueError(
                    "completed 상태에는 최종 결과만 "
                    "존재해야 합니다.",
                )

            return self

        if self.status == "failed":
            if self.retryable:
                raise ValueError(
                    "failed 상태는 "
                    "retryable=false여야 합니다.",
                )

            if self.reason_code not in {
                "MODEL_UNAVAILABLE",
                "INTERNAL_ERROR",
            }:
                raise ValueError(
                    "failed 상태의 reason_code는 "
                    "MODEL_UNAVAILABLE 또는 "
                    "INTERNAL_ERROR여야 합니다.",
                )

            if (
                self.retry_items
                or self.result is not None
            ):
                raise ValueError(
                    "failed 상태에는 재시도 항목이나 "
                    "최종 결과가 없어야 합니다.",
                )

            return self

        raise ValueError(
            "지원하지 않는 분석 상태입니다.",
        )
