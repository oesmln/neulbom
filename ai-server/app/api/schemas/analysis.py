from datetime import date, datetime
from math import isfinite
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
