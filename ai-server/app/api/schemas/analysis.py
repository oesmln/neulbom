from datetime import date, datetime
from typing import Annotated, Literal
from uuid import UUID

from pydantic import (
    Field,
    field_validator,
)

from app.api.schemas.common import (
    APIModel,
    AudioResource,
    ConditionalQuestionCode,
    MemoryUnitMap,
    QuestionCode,
    QuestionSetVersion,
    ResponseTiming,
    SttConfig,
    SttInput,
    Timezone,
    WrongEventRuleVersion,
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