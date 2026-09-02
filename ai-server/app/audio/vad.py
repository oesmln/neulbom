from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from functools import lru_cache
from pathlib import Path
from threading import Lock
from typing import Any, Callable, Literal

import numpy as np
import torch
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from silero_vad import get_speech_timestamps, load_silero_vad

from app.audio.preprocessing import (
    TARGET_SAMPLE_RATE,
    ProcessedAudio,
)
from app.core.config import PROJECT_ROOT

DEFAULT_VAD_CONFIG_PATH = (
    PROJECT_ROOT / "configs" / "vad-v1.json"
)

TimestampGetter = Callable[..., list[dict[str, int]]]


class VadModelConfig(BaseModel):
    model_config = ConfigDict(
        frozen=True,
        extra="forbid",
    )

    name: Literal["silero-vad"]
    package_version: str = Field(min_length=1)
    runtime: Literal["onnx-cpu"]
    opset_version: int = Field(ge=1)


class VadAudioConfig(BaseModel):
    model_config = ConfigDict(
        frozen=True,
        extra="forbid",
    )

    sample_rate_hz: Literal[16000]


class VadDetectionConfig(BaseModel):
    model_config = ConfigDict(
        frozen=True,
        extra="forbid",
    )

    threshold: float = Field(
        gt=0,
        lt=1,
    )
    min_speech_duration_ms: int = Field(ge=1)
    min_silence_duration_ms: int = Field(ge=0)
    speech_pad_ms: int = Field(ge=0)


class VadResponseDelayConfig(BaseModel):
    model_config = ConfigDict(
        frozen=True,
        extra="forbid",
    )

    onset_bias_correction_ms: int = Field(ge=0)


class VadConfig(BaseModel):
    model_config = ConfigDict(
        frozen=True,
        extra="forbid",
    )

    schema_version: Literal["vad-runtime-config-v1"]
    config_version: str = Field(min_length=1)
    model: VadModelConfig
    audio: VadAudioConfig
    detection: VadDetectionConfig
    response_delay: VadResponseDelayConfig
    calibration_report: str = Field(min_length=1)


class VadStatus(StrEnum):
    SPEECH_DETECTED = "speech_detected"
    NO_SPEECH_DETECTED = "no_speech_detected"


@dataclass(frozen=True, slots=True)
class VadResult:
    status: VadStatus
    raw_onset_ms: int | None
    corrected_onset_ms: int | None
    prompt_end_to_recording_start_ms: int
    response_delay_ms: int | None
    vad_config_version: str


class VadConfigurationError(RuntimeError):
    pass


def load_vad_config(
    path: Path = DEFAULT_VAD_CONFIG_PATH,
) -> VadConfig:
    try:
        content = path.read_text(encoding="utf-8")
    except OSError as error:
        raise VadConfigurationError(
            f"VAD 설정 파일을 읽을 수 없습니다: {path}",
        ) from error

    try:
        return VadConfig.model_validate_json(content)
    except ValidationError as error:
        raise VadConfigurationError(
            f"VAD 설정 파일이 올바르지 않습니다: {path}",
        ) from error


class VadService:
    def __init__(
        self,
        *,
        config: VadConfig,
        model: Any,
        timestamp_getter: TimestampGetter = (
            get_speech_timestamps
        ),
    ) -> None:
        self._config = config
        self._model = model
        self._timestamp_getter = timestamp_getter

        # Silero 모델의 내부 상태가 동시 요청에서 섞이지 않도록
        # 하나의 추론이 끝난 뒤 다음 추론을 실행한다.
        self._inference_lock = Lock()

    @classmethod
    def from_config_path(
        cls,
        path: Path = DEFAULT_VAD_CONFIG_PATH,
    ) -> VadService:
        config = load_vad_config(path)

        model = load_silero_vad(
            onnx=True,
            opset_version=config.model.opset_version,
        )

        return cls(
            config=config,
            model=model,
        )

    @property
    def config(self) -> VadConfig:
        return self._config

    def detect_response_onset(
        self,
        *,
        audio: ProcessedAudio,
        prompt_end_to_recording_start_ms: int,
    ) -> VadResult:
        self._validate_input(
            audio=audio,
            prompt_end_to_recording_start_ms=(
                prompt_end_to_recording_start_ms
            ),
        )

        waveform = np.ascontiguousarray(
            audio.waveform,
            dtype=np.float32,
        )
        tensor = torch.from_numpy(waveform)

        with self._inference_lock:
            timestamps = self._timestamp_getter(
                tensor,
                self._model,
                threshold=(
                    self._config.detection.threshold
                ),
                sampling_rate=(
                    self._config.audio.sample_rate_hz
                ),
                min_speech_duration_ms=(
                    self._config
                    .detection
                    .min_speech_duration_ms
                ),
                min_silence_duration_ms=(
                    self._config
                    .detection
                    .min_silence_duration_ms
                ),
                speech_pad_ms=(
                    self._config.detection.speech_pad_ms
                ),
                return_seconds=False,
            )

        if not timestamps:
            return VadResult(
                status=VadStatus.NO_SPEECH_DETECTED,
                raw_onset_ms=None,
                corrected_onset_ms=None,
                prompt_end_to_recording_start_ms=(
                    prompt_end_to_recording_start_ms
                ),
                response_delay_ms=None,
                vad_config_version=(
                    self._config.config_version
                ),
            )

        first_start_sample = int(
            timestamps[0]["start"],
        )

        if not (
            0
            <= first_start_sample
            < waveform.size
        ):
            raise RuntimeError(
                "VAD가 음성 범위를 벗어난 시작 시각을 "
                "반환했습니다.",
            )

        raw_onset_ms = round(
            first_start_sample
            / self._config.audio.sample_rate_hz
            * 1000,
        )
        corrected_onset_ms = max(
            0,
            raw_onset_ms
            - self._config
            .response_delay
            .onset_bias_correction_ms,
        )
        response_delay_ms = (
            prompt_end_to_recording_start_ms
            + corrected_onset_ms
        )

        return VadResult(
            status=VadStatus.SPEECH_DETECTED,
            raw_onset_ms=raw_onset_ms,
            corrected_onset_ms=corrected_onset_ms,
            prompt_end_to_recording_start_ms=(
                prompt_end_to_recording_start_ms
            ),
            response_delay_ms=response_delay_ms,
            vad_config_version=(
                self._config.config_version
            ),
        )

    def _validate_input(
        self,
        *,
        audio: ProcessedAudio,
        prompt_end_to_recording_start_ms: int,
    ) -> None:
        if prompt_end_to_recording_start_ms < 0:
            raise ValueError(
                "prompt_end_to_recording_start_ms는 "
                "0 이상이어야 합니다.",
            )

        if (
            audio.sample_rate
            != self._config.audio.sample_rate_hz
        ):
            raise ValueError(
                "VAD 입력 음성의 sample rate가 "
                "설정과 일치하지 않습니다.",
            )

        if audio.waveform.ndim != 1:
            raise ValueError(
                "VAD 입력 음성은 mono 1차원 배열이어야 "
                "합니다.",
            )

        if audio.waveform.size == 0:
            raise ValueError(
                "VAD 입력 음성이 비어 있습니다.",
            )

        if not np.isfinite(audio.waveform).all():
            raise ValueError(
                "VAD 입력 음성에 유효하지 않은 값이 "
                "있습니다.",
            )


@lru_cache
def get_vad_service(
    config_path: Path = DEFAULT_VAD_CONFIG_PATH,
) -> VadService:
    """
    동일 프로세스에서 VAD 모델을 한 번만 생성한다.
    """

    return VadService.from_config_path(config_path)