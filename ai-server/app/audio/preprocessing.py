from dataclasses import dataclass
from enum import StrEnum
from io import BytesIO

import av
import numpy as np
from av.audio.resampler import AudioResampler

TARGET_SAMPLE_RATE = 16_000
AST_SEGMENT_SECONDS = 4.0
AST_MIN_REMAINDER_SECONDS = 1.0
MAX_AUDIO_DURATION_SECONDS = 60.0

_FORMAT_HINTS = {
    "audio/wav": "wav",
    "audio/x-wav": "wav",
    "audio/mp4": "mp4",
    "audio/x-m4a": "mp4",
    "audio/mpeg": "mp3",
    "audio/webm": "webm",
}


class AudioPreprocessingReason(StrEnum):
    UNSUPPORTED_AUDIO_FORMAT = (
        "UNSUPPORTED_AUDIO_FORMAT"
    )


@dataclass(frozen=True, slots=True)
class ProcessedAudio:
    waveform: np.ndarray
    sample_rate: int
    duration_ms: int
    source_content_type: str


@dataclass(frozen=True, slots=True)
class AudioSegment:
    index: int
    waveform: np.ndarray
    valid_samples: int


class AudioPreprocessingError(RuntimeError):
    def __init__(
        self,
        *,
        reason_code: AudioPreprocessingReason,
        message: str,
        retryable: bool,
    ) -> None:
        super().__init__(message)
        self.reason_code = reason_code
        self.retryable = retryable


def preprocess_audio(
    *,
    content: bytes,
    content_type: str,
    target_sample_rate: int = TARGET_SAMPLE_RATE,
    max_duration_seconds: float = (
        MAX_AUDIO_DURATION_SECONDS
    ),
) -> ProcessedAudio:
    if not content:
        raise AudioPreprocessingError(
            reason_code=(
                AudioPreprocessingReason
                .UNSUPPORTED_AUDIO_FORMAT
            ),
            message="음성 파일이 비어 있습니다.",
            retryable=False,
        )

    if target_sample_rate <= 0:
        raise ValueError(
            "target_sample_rate는 1 이상이어야 합니다.",
        )

    if max_duration_seconds <= 0:
        raise ValueError(
            "max_duration_seconds는 0보다 커야 합니다.",
        )

    normalized_content_type = (
        content_type
        .split(";", maxsplit=1)[0]
        .strip()
        .lower()
    )
    format_hint = _FORMAT_HINTS.get(
        normalized_content_type,
    )

    if format_hint is None:
        raise AudioPreprocessingError(
            reason_code=(
                AudioPreprocessingReason
                .UNSUPPORTED_AUDIO_FORMAT
            ),
            message="지원하지 않는 음성 형식입니다.",
            retryable=False,
        )

    waveform = _decode_audio(
        content=content,
        format_hint=format_hint,
        target_sample_rate=target_sample_rate,
        max_duration_seconds=max_duration_seconds,
    )

    if waveform.size == 0:
        raise AudioPreprocessingError(
            reason_code=(
                AudioPreprocessingReason
                .UNSUPPORTED_AUDIO_FORMAT
            ),
            message="디코딩된 음성 데이터가 없습니다.",
            retryable=False,
        )

    if not np.isfinite(waveform).all():
        raise AudioPreprocessingError(
            reason_code=(
                AudioPreprocessingReason
                .UNSUPPORTED_AUDIO_FORMAT
            ),
            message="음성 데이터에 유효하지 않은 값이 있습니다.",
            retryable=False,
        )

    waveform = np.ascontiguousarray(
        waveform,
        dtype=np.float32,
    )
    duration_ms = round(
        waveform.size
        / target_sample_rate
        * 1000,
    )

    return ProcessedAudio(
        waveform=waveform,
        sample_rate=target_sample_rate,
        duration_ms=duration_ms,
        source_content_type=normalized_content_type,
    )


def create_ast_segments(
    audio: ProcessedAudio,
    *,
    segment_seconds: float = AST_SEGMENT_SECONDS,
    min_remainder_seconds: float = (
        AST_MIN_REMAINDER_SECONDS
    ),
) -> tuple[AudioSegment, ...]:
    if audio.waveform.ndim != 1:
        raise ValueError(
            "전처리 음성은 mono 1차원 배열이어야 합니다.",
        )

    if audio.waveform.size == 0:
        raise ValueError(
            "전처리 음성이 비어 있습니다.",
        )

    if segment_seconds <= 0:
        raise ValueError(
            "segment_seconds는 0보다 커야 합니다.",
        )

    if not (
        0 < min_remainder_seconds
        <= segment_seconds
    ):
        raise ValueError(
            "min_remainder_seconds는 0보다 크고 "
            "segment_seconds 이하여야 합니다.",
        )

    segment_samples = round(
        audio.sample_rate * segment_seconds,
    )
    minimum_remainder_samples = round(
        audio.sample_rate * min_remainder_seconds,
    )

    full_segments, remainder = divmod(
        audio.waveform.size,
        segment_samples,
    )
    segment_count = full_segments

    if remainder >= minimum_remainder_samples:
        segment_count += 1

    # 학습 코드와 동일하게 짧은 응답도 최소 1개를 만든다.
    segment_count = max(1, segment_count)

    segments: list[AudioSegment] = []

    for index in range(segment_count):
        start = index * segment_samples
        end = min(
            start + segment_samples,
            audio.waveform.size,
        )
        waveform = audio.waveform[start:end]
        valid_samples = waveform.size

        if valid_samples < segment_samples:
            waveform = np.pad(
                waveform,
                (
                    0,
                    segment_samples - valid_samples,
                ),
                mode="constant",
            )

        segments.append(
            AudioSegment(
                index=index,
                waveform=np.ascontiguousarray(
                    waveform,
                    dtype=np.float32,
                ),
                valid_samples=valid_samples,
            ),
        )

    return tuple(segments)


def _decode_audio(
    *,
    content: bytes,
    format_hint: str,
    target_sample_rate: int,
    max_duration_seconds: float,
) -> np.ndarray:
    maximum_samples = round(
        target_sample_rate * max_duration_seconds,
    )
    chunks: list[np.ndarray] = []
    sample_count = 0

    try:
        with av.open(
            BytesIO(content),
            mode="r",
            format=format_hint,
        ) as container:
            audio_streams = list(
                container.streams.audio,
            )

            if not audio_streams:
                raise AudioPreprocessingError(
                    reason_code=(
                        AudioPreprocessingReason
                        .UNSUPPORTED_AUDIO_FORMAT
                    ),
                    message=(
                        "파일에 음성 스트림이 없습니다."
                    ),
                    retryable=False,
                )

            stream = audio_streams[0]
            resampler = AudioResampler(
                format="fltp",
                layout="mono",
                rate=target_sample_rate,
            )

            for frame in container.decode(stream):
                output_frames = resampler.resample(
                    frame,
                )

                sample_count = _append_frames(
                    output_frames=output_frames,
                    chunks=chunks,
                    current_sample_count=sample_count,
                    maximum_samples=maximum_samples,
                )

            flushed_frames = resampler.resample(None)

            _append_frames(
                output_frames=flushed_frames,
                chunks=chunks,
                current_sample_count=sample_count,
                maximum_samples=maximum_samples,
            )

    except AudioPreprocessingError:
        raise
    except (
        av.FFmpegError,
        EOFError,
        OSError,
        ValueError,
    ) as error:
        raise AudioPreprocessingError(
            reason_code=(
                AudioPreprocessingReason
                .UNSUPPORTED_AUDIO_FORMAT
            ),
            message="음성 파일을 디코딩하지 못했습니다.",
            retryable=False,
        ) from error

    if not chunks:
        return np.empty(
            0,
            dtype=np.float32,
        )

    return np.concatenate(chunks)


def _append_frames(
    *,
    output_frames,
    chunks: list[np.ndarray],
    current_sample_count: int,
    maximum_samples: int,
) -> int:
    sample_count = current_sample_count

    for frame in output_frames:
        chunk = (
            frame.to_ndarray()
            .reshape(-1)
            .astype(np.float32, copy=False)
        )

        if chunk.size == 0:
            continue

        sample_count += chunk.size

        if sample_count > maximum_samples:
            raise AudioPreprocessingError(
                reason_code=(
                    AudioPreprocessingReason
                    .UNSUPPORTED_AUDIO_FORMAT
                ),
                message=(
                    "음성 길이가 허용된 최대 길이를 "
                    "초과했습니다."
                ),
                retryable=False,
            )

        chunks.append(chunk)

    return sample_count
