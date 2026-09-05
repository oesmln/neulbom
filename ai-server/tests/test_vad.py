import json
from pathlib import Path
from typing import Any

import numpy as np
import pytest

from app.audio.preprocessing import ProcessedAudio
from app.audio.vad import (
    VadConfigurationError,
    VadConfig,
    VadService,
    VadStatus,
    load_vad_config,
)


def test_loads_vad_config(
    tmp_path: Path,
) -> None:
    config_path = tmp_path / "vad-v1.json"
    config_path.write_text(
        json.dumps(
            _config_payload(),
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    config = load_vad_config(config_path)

    assert config.config_version == "vad-v1"
    assert config.detection.threshold == 0.2
    assert (
        config.detection.min_speech_duration_ms
        == 100
    )
    assert (
        config.response_delay.onset_bias_correction_ms
        == 24
    )


def test_rejects_invalid_vad_config(
    tmp_path: Path,
) -> None:
    payload = _config_payload()
    payload["detection"]["threshold"] = 0

    config_path = tmp_path / "invalid-vad.json"
    config_path.write_text(
        json.dumps(payload),
        encoding="utf-8",
    )

    with pytest.raises(
        VadConfigurationError,
        match="올바르지 않습니다",
    ):
        load_vad_config(config_path)


def test_detects_and_corrects_response_onset() -> None:
    captured: dict[str, Any] = {}

    def timestamp_getter(
        waveform,
        model,
        **kwargs,
    ) -> list[dict[str, int]]:
        captured["sample_count"] = waveform.numel()
        captured["model"] = model
        captured.update(kwargs)

        return [
            {
                # 1,984 / 16,000초 = 124ms
                "start": 1_984,
                "end": 4_000,
            },
        ]

    model = object()
    service = VadService(
        config=_config(),
        model=model,
        timestamp_getter=timestamp_getter,
    )

    result = service.detect_response_onset(
        audio=_processed_audio(),
        prompt_end_to_recording_start_ms=250,
    )

    assert result.status == VadStatus.SPEECH_DETECTED
    assert result.raw_onset_ms == 124
    assert result.corrected_onset_ms == 100
    assert result.response_delay_ms == 350
    assert result.vad_config_version == "vad-v1"

    assert captured["sample_count"] == 16_000
    assert captured["model"] is model
    assert captured["threshold"] == 0.2
    assert captured["sampling_rate"] == 16_000
    assert captured["min_speech_duration_ms"] == 100
    assert captured["min_silence_duration_ms"] == 100
    assert captured["speech_pad_ms"] == 0
    assert captured["return_seconds"] is False


def test_clamps_corrected_onset_to_zero() -> None:
    def timestamp_getter(
        waveform,
        model,
        **kwargs,
    ) -> list[dict[str, int]]:
        return [
            {
                # 녹음 시작 후 10ms
                "start": 160,
                "end": 2_000,
            },
        ]

    service = VadService(
        config=_config(),
        model=object(),
        timestamp_getter=timestamp_getter,
    )

    result = service.detect_response_onset(
        audio=_processed_audio(),
        prompt_end_to_recording_start_ms=300,
    )

    assert result.raw_onset_ms == 10
    assert result.corrected_onset_ms == 0
    assert result.response_delay_ms == 300


def test_returns_no_speech_detected() -> None:
    def timestamp_getter(
        waveform,
        model,
        **kwargs,
    ) -> list[dict[str, int]]:
        return []

    service = VadService(
        config=_config(),
        model=object(),
        timestamp_getter=timestamp_getter,
    )

    result = service.detect_response_onset(
        audio=_processed_audio(),
        prompt_end_to_recording_start_ms=250,
    )

    assert (
        result.status
        == VadStatus.NO_SPEECH_DETECTED
    )
    assert result.raw_onset_ms is None
    assert result.corrected_onset_ms is None
    assert result.response_delay_ms is None


def test_rejects_negative_prompt_gap() -> None:
    service = VadService(
        config=_config(),
        model=object(),
        timestamp_getter=lambda *args, **kwargs: [],
    )

    with pytest.raises(
        ValueError,
        match="0 이상",
    ):
        service.detect_response_onset(
            audio=_processed_audio(),
            prompt_end_to_recording_start_ms=-1,
        )


def test_rejects_unexpected_sample_rate() -> None:
    audio = ProcessedAudio(
        waveform=np.zeros(
            8_000,
            dtype=np.float32,
        ),
        sample_rate=8_000,
        duration_ms=1_000,
        source_content_type="audio/wav",
    )
    service = VadService(
        config=_config(),
        model=object(),
        timestamp_getter=lambda *args, **kwargs: [],
    )

    with pytest.raises(
        ValueError,
        match="sample rate",
    ):
        service.detect_response_onset(
            audio=audio,
            prompt_end_to_recording_start_ms=0,
        )


def _processed_audio() -> ProcessedAudio:
    return ProcessedAudio(
        waveform=np.zeros(
            16_000,
            dtype=np.float32,
        ),
        sample_rate=16_000,
        duration_ms=1_000,
        source_content_type="audio/wav",
    )


def _config() -> VadConfig:
    return VadConfig.model_validate(
        _config_payload(),
    )


def _config_payload() -> dict[str, Any]:
    return {
        "schema_version": "vad-runtime-config-v1",
        "config_version": "vad-v1",
        "model": {
            "name": "silero-vad",
            "package_version": "6.2.1",
            "runtime": "onnx-cpu",
            "opset_version": 16,
        },
        "audio": {
            "sample_rate_hz": 16_000,
        },
        "detection": {
            "threshold": 0.2,
            "min_speech_duration_ms": 100,
            "min_silence_duration_ms": 100,
            "speech_pad_ms": 0,
        },
        "response_delay": {
            "onset_bias_correction_ms": 24,
        },
        "calibration_report": (
            "validation/vad-calibration-v1.json"
        ),
    }