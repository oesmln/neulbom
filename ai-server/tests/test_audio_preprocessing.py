import math
import struct
import wave
from io import BytesIO

import av
import numpy as np
import pytest

from app.audio.preprocessing import (
    AudioPreprocessingError,
    AudioPreprocessingReason,
    ProcessedAudio,
    create_ast_segments,
    preprocess_audio,
)


def test_converts_stereo_8khz_wav_to_mono_16khz() -> None:
    content = _create_wav(
        sample_rate=8_000,
        channels=2,
        duration_seconds=0.25,
    )

    result = preprocess_audio(
        content=content,
        content_type="audio/wav",
    )

    assert result.sample_rate == 16_000
    assert result.waveform.ndim == 1
    assert result.waveform.dtype == np.float32
    assert result.waveform.flags.c_contiguous
    assert np.isfinite(result.waveform).all()
    assert 240 <= result.duration_ms <= 260


def test_decodes_webm_opus_to_mono_16khz() -> None:
    content = _create_webm(
        sample_rate=48_000,
        duration_seconds=0.25,
    )

    result = preprocess_audio(
        content=content,
        content_type="audio/webm; codecs=opus",
    )

    assert result.sample_rate == 16_000
    assert result.waveform.ndim == 1
    assert result.waveform.dtype == np.float32
    assert result.waveform.flags.c_contiguous
    assert np.isfinite(result.waveform).all()
    assert 240 <= result.duration_ms <= 260


def test_rejects_unsupported_content_type() -> None:
    with pytest.raises(
        AudioPreprocessingError,
    ) as captured:
        preprocess_audio(
            content=b"audio",
            content_type="audio/webm",
        )

    assert captured.value.reason_code == (
        AudioPreprocessingReason
        .UNSUPPORTED_AUDIO_FORMAT
    )
    assert captured.value.retryable is False


def test_rejects_invalid_audio_bytes() -> None:
    with pytest.raises(
        AudioPreprocessingError,
    ) as captured:
        preprocess_audio(
            content=b"not-audio-data",
            content_type="audio/wav",
        )

    assert captured.value.reason_code == (
        AudioPreprocessingReason
        .UNSUPPORTED_AUDIO_FORMAT
    )


def test_rejects_empty_audio() -> None:
    with pytest.raises(
        AudioPreprocessingError,
        match="비어",
    ):
        preprocess_audio(
            content=b"",
            content_type="audio/wav",
        )


def test_creates_four_second_ast_segments() -> None:
    audio = _processed_audio(
        duration_seconds=5.5,
    )

    segments = create_ast_segments(audio)

    assert len(segments) == 2
    assert segments[0].waveform.size == 64_000
    assert segments[0].valid_samples == 64_000
    assert segments[1].waveform.size == 64_000
    assert segments[1].valid_samples == 24_000
    assert np.all(
        segments[1].waveform[24_000:] == 0,
    )


def test_discards_remainder_shorter_than_one_second() -> None:
    audio = _processed_audio(
        duration_seconds=4.5,
    )

    segments = create_ast_segments(audio)

    assert len(segments) == 1
    assert segments[0].valid_samples == 64_000


def test_short_response_still_creates_one_segment() -> None:
    audio = _processed_audio(
        duration_seconds=0.5,
    )

    segments = create_ast_segments(audio)

    assert len(segments) == 1
    assert segments[0].waveform.size == 64_000
    assert segments[0].valid_samples == 8_000
    assert np.all(
        segments[0].waveform[8_000:] == 0,
    )


def _processed_audio(
    *,
    duration_seconds: float,
) -> ProcessedAudio:
    sample_rate = 16_000
    sample_count = round(
        sample_rate * duration_seconds,
    )

    return ProcessedAudio(
        waveform=np.ones(
            sample_count,
            dtype=np.float32,
        ),
        sample_rate=sample_rate,
        duration_ms=round(
            duration_seconds * 1000,
        ),
        source_content_type="audio/wav",
    )


def _create_wav(
    *,
    sample_rate: int,
    channels: int,
    duration_seconds: float,
) -> bytes:
    sample_count = round(
        sample_rate * duration_seconds,
    )
    frames = bytearray()

    for index in range(sample_count):
        sample = round(
            12_000
            * math.sin(
                2
                * math.pi
                * 440
                * index
                / sample_rate
            ),
        )

        for _ in range(channels):
            frames.extend(
                struct.pack("<h", sample),
            )

    buffer = BytesIO()

    with wave.open(buffer, "wb") as wav_file:
        wav_file.setnchannels(channels)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        wav_file.writeframes(bytes(frames))

    return buffer.getvalue()


def _create_webm(
    *,
    sample_rate: int,
    duration_seconds: float,
) -> bytes:
    sample_count = round(
        sample_rate * duration_seconds,
    )
    samples = (
        12_000
        * np.sin(
            2
            * math.pi
            * 440
            * np.arange(sample_count)
            / sample_rate,
        )
    ).astype(np.int16)
    buffer = BytesIO()

    with av.open(buffer, mode="w", format="webm") as container:
        stream = container.add_stream(
            "libopus",
            rate=sample_rate,
        )
        stream.layout = "mono"
        frame = av.AudioFrame.from_ndarray(
            samples.reshape(1, -1),
            format="s16",
            layout="mono",
        )
        frame.sample_rate = sample_rate

        for packet in stream.encode(frame):
            container.mux(packet)
        for packet in stream.encode():
            container.mux(packet)

    return buffer.getvalue()
