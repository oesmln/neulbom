from datetime import date
from typing import Annotated, Literal
from uuid import UUID

from pydantic import Field, model_validator

from app.api.schemas.common import (
    APIModel,
    AudioResource,
    ConditionalQuestionCode,
    MemoryUnitMap,
    QuestionAnalysisResult,
    QuestionSetVersion,
    ResponseTiming,
    RetryReasonCode,
    SttConfig,
    SttInput,
    Timezone,
    WrongEventRuleVersion,
)

MEMORY_UNIT_TO_RECOGNITION_CODE = {
    "person": "memory_recognition_person",
    "transport": "memory_recognition_transport",
    "place": "memory_recognition_place",
    "time": "memory_recognition_time",
    "activity": "memory_recognition_activity",
}


class Q11ResponseInput(APIModel):
    question_code: Literal[
        "memory_delayed_free_recall"
    ]
    variant_id: Literal[
        "memory-delayed-free-recall-fixed-v1"
    ]
    administration_status: Literal["administered"]
    recording_id: UUID
    response_id: UUID
    audio: AudioResource
    stt: SttInput
    timing: ResponseTiming


class RecognitionPlanRequest(APIModel):
    question_set_version: QuestionSetVersion
    wrong_event_rule_version: WrongEventRuleVersion
    assessment_local_date: date
    timezone: Timezone
    stt_config: SttConfig
    response: Q11ResponseInput


class RecognitionPlanCompletedResponse(APIModel):
    assessment_id: UUID
    status: Literal["completed"]
    question_set_version: QuestionSetVersion
    wrong_event_rule_version: WrongEventRuleVersion
    recalled_units: MemoryUnitMap
    next_question_codes: list[
        ConditionalQuestionCode
    ] = Field(
        min_length=0,
        max_length=5,
    )
    q11_result: QuestionAnalysisResult

    @model_validator(mode="after")
    def validate_recognition_mapping(
        self,
    ) -> "RecognitionPlanCompletedResponse":
        if (
            len(self.next_question_codes)
            != len(set(self.next_question_codes))
        ):
            raise ValueError(
                "next_question_codes에는 "
                "중복값이 있을 수 없습니다.",
            )

        expected_codes = {
            question_code
            for unit_code, question_code
            in MEMORY_UNIT_TO_RECOGNITION_CODE.items()
            if not getattr(
                self.recalled_units,
                unit_code,
            )
        }

        actual_codes = set(
            self.next_question_codes,
        )

        if actual_codes != expected_codes:
            raise ValueError(
                "next_question_codes는 recalled_units가 "
                "false인 기억 단위와 정확히 일치해야 합니다.",
            )

        if (
            self.q11_result.question_code
            != "memory_delayed_free_recall"
        ):
            raise ValueError(
                "q11_result.question_code는 "
                "memory_delayed_free_recall이어야 합니다.",
            )

        if (
            self.q11_result.recognized_memory_units
            != self.recalled_units
        ):
            raise ValueError(
                "q11_result.recognized_memory_units와 "
                "recalled_units가 일치해야 합니다.",
            )

        return self


class RecognitionPlanNeedsRetryResponse(APIModel):
    assessment_id: UUID
    status: Literal["needs_retry"]
    question_set_version: QuestionSetVersion
    wrong_event_rule_version: WrongEventRuleVersion
    reason_code: RetryReasonCode
    retryable: Literal[True]
    retry_question_codes: list[
        Literal["memory_delayed_free_recall"]
    ] = Field(
        min_length=1,
        max_length=1,
    )


RecognitionPlanResponse = Annotated[
    RecognitionPlanCompletedResponse
    | RecognitionPlanNeedsRetryResponse,
    Field(discriminator="status"),
]