from datetime import datetime
from typing import Annotated, Literal
from uuid import UUID

from pydantic import (
    AnyHttpUrl,
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    field_validator,
    model_validator,
)

QuestionSetVersion = Literal["cist-v1"]
WrongEventRuleVersion = Literal["wrong-event-v1"]
Timezone = Literal["Asia/Seoul"]

QuestionCode = Literal[
    "orientation_year",
    "orientation_month",
    "orientation_day",
    "orientation_weekday",
    "orientation_place",
    "memory_registration_first",
    "memory_registration_second",
    "attention_digit_span_4",
    "attention_digit_span_5",
    "attention_word_reverse",
    "memory_delayed_free_recall",
    "memory_recognition_person",
    "memory_recognition_transport",
    "memory_recognition_place",
    "memory_recognition_time",
    "memory_recognition_activity",
    "language_semantic_fluency",
]

ConditionalQuestionCode = Literal[
    "memory_recognition_person",
    "memory_recognition_transport",
    "memory_recognition_place",
    "memory_recognition_time",
    "memory_recognition_activity",
]

RetryReasonCode = Literal[
    "INCOMPLETE_ASSESSMENT",
    "UNSCORABLE_STT",
    "AUDIO_URL_EXPIRED",
    "AUDIO_DOWNLOAD_FAILED",
    "UNSUPPORTED_AUDIO_FORMAT",
]

IdempotencyKey = Annotated[
    str,
    StringConstraints(
        strip_whitespace=True,
        min_length=16,
        max_length=200,
    ),
]

AudioContentType = Literal[
    "audio/wav",
    "audio/x-wav",
    "audio/mp4",
    "audio/x-m4a",
    "audio/mpeg",
]


class APIModel(BaseModel):
    """모든 API 스키마의 공통 설정."""

    model_config = ConfigDict(
        extra="forbid",
    )


class MemoryUnitMap(APIModel):
    person: bool
    transport: bool
    place: bool
    time: bool
    activity: bool


class AudioResource(APIModel):
    signed_url: AnyHttpUrl
    expires_at: datetime
    content_type: AudioContentType
    size_bytes: int = Field(ge=1)
    sha256: str | None = Field(
        default=None,
        pattern=r"^[A-Fa-f0-9]{64}$",
    )

    @field_validator("signed_url")
    @classmethod
    def require_https_signed_url(
        cls,
        value: AnyHttpUrl,
    ) -> AnyHttpUrl:
        if value.scheme != "https":
            raise ValueError(
                "signed_url은 HTTPS URL이어야 합니다.",
            )

        return value


class SttConfig(APIModel):
    provider: Literal["google"]
    api_version: Literal["v2"]
    location: Literal["us"]
    model: Literal["chirp_3"]
    language: Literal["ko-KR"]
    automatic_punctuation: Literal[True]


class SttInput(APIModel):
    status: Literal[
        "success",
        "empty_transcript",
        "failed",
    ]
    raw_transcript: str | None
    provider_request_id: str | None = None

    @model_validator(mode="after")
    def validate_transcript_status(
        self,
    ) -> "SttInput":
        transcript = self.raw_transcript

        if self.status == "success":
            if transcript is None or not transcript.strip():
                raise ValueError(
                    "stt.status=success이면 "
                    "raw_transcript가 필요합니다.",
                )

        elif self.status == "empty_transcript":
            if transcript is not None and transcript.strip():
                raise ValueError(
                    "stt.status=empty_transcript이면 "
                    "raw_transcript는 null 또는 빈 문자열이어야 합니다.",
                )

        elif self.status == "failed":
            if transcript is not None:
                raise ValueError(
                    "stt.status=failed이면 "
                    "raw_transcript는 null이어야 합니다.",
                )

        return self


class ResponseTiming(APIModel):
    prompt_end_to_recording_start_ms: int = Field(
        ge=0,
    )
    recording_duration_ms: int = Field(
        ge=1,
        le=60_000,
    )
    client_timing_source: Literal[
        "monotonic_clock"
    ] = "monotonic_clock"


class QuestionAnalysisResult(APIModel):
    question_code: QuestionCode
    administration_status: Literal[
        "administered",
        "not_applicable",
    ]
    recording_id: UUID | None
    response_id: UUID | None
    vad_status: Literal[
        "speech_detected",
        "no_response",
    ] | None
    scoring_status: Literal[
        "scored",
        "not_scored",
    ] | None
    answer_status: Literal[
        "correct",
        "incorrect",
    ] | None
    wrong_event: Literal[0, 1] | None
    wrong_event_reason: Literal[
        "OBJECTIVE_INCORRECT",
        "EXPLICIT_FAILURE_EXPRESSION",
        "NO_RELEVANT_MEMORY_UNIT",
    ] | None
    response_delay_ms: int | None = Field(
        default=None,
        ge=0,
    )
    recognized_memory_units: (
        MemoryUnitMap | None
    )

    @model_validator(mode="after")
    def validate_result_state(
        self,
    ) -> "QuestionAnalysisResult":
        if (
            self.administration_status
            == "not_applicable"
        ):
            values = (
                self.recording_id,
                self.response_id,
                self.vad_status,
                self.scoring_status,
                self.answer_status,
                self.wrong_event,
                self.wrong_event_reason,
                self.response_delay_ms,
                self.recognized_memory_units,
            )

            if any(
                value is not None
                for value in values
            ):
                raise ValueError(
                    "not_applicable 문항의 분석 "
                    "필드는 모두 null이어야 합니다.",
                )

            return self

        if (
            self.recording_id is None
            or self.response_id is None
            or self.vad_status is None
            or self.scoring_status is None
        ):
            raise ValueError(
                "administered 문항에는 녹음·응답 "
                "식별자와 VAD·채점 상태가 필요합니다.",
            )

        if self.vad_status == "no_response":
            if self.response_delay_ms is not None:
                raise ValueError(
                    "no_response 문항에는 "
                    "응답 지연이 없어야 합니다.",
                )
        elif self.response_delay_ms is None:
            raise ValueError(
                "speech_detected 문항에는 "
                "응답 지연이 필요합니다.",
            )

        if self.scoring_status == "scored":
            if self.answer_status is None:
                raise ValueError(
                    "scored 문항에는 "
                    "answer_status가 필요합니다.",
                )
        elif self.answer_status is not None:
            raise ValueError(
                "not_scored 문항의 "
                "answer_status는 null이어야 합니다.",
            )

        if self.wrong_event == 1:
            if self.wrong_event_reason is None:
                raise ValueError(
                    "wrong_event=1이면 "
                    "사유가 필요합니다.",
                )
        elif self.wrong_event_reason is not None:
            raise ValueError(
                "wrong_event가 1이 아니면 "
                "사유는 null이어야 합니다.",
            )

        return self