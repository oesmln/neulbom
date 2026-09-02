from dataclasses import dataclass
from typing import Any

import numpy as np


@dataclass(frozen=True, slots=True)
class VadCandidate:
    threshold: float
    min_speech_duration_ms: int
    min_silence_duration_ms: int = 100
    speech_pad_ms: int = 0

    @property
    def identifier(self) -> str:
        threshold_text = str(self.threshold).replace(
            ".",
            "_",
        )

        return (
            f"threshold_{threshold_text}"
            f"_speech_{self.min_speech_duration_ms}ms"
        )


@dataclass(frozen=True, slots=True)
class VadObservation:
    filename: str
    expected_onset_ms: int
    detected_onset_ms: int | None

    @property
    def error_ms(self) -> int | None:
        if self.detected_onset_ms is None:
            return None

        return (
            self.detected_onset_ms
            - self.expected_onset_ms
        )


def summarize_candidate(
    *,
    candidate: VadCandidate,
    observations: list[VadObservation],
    early_tolerance_ms: int = 100,
) -> dict[str, Any]:
    if not observations:
        raise ValueError(
            "VAD 관측값이 하나 이상 필요합니다.",
        )

    detected = [
        observation
        for observation in observations
        if observation.detected_onset_ms is not None
    ]
    missed = [
        observation
        for observation in observations
        if observation.detected_onset_ms is None
    ]

    errors = np.asarray(
        [
            observation.error_ms
            for observation in detected
        ],
        dtype=np.float64,
    )
    absolute_errors = np.abs(errors)

    false_early = [
        observation
        for observation in detected
        if (
            observation.error_ms is not None
            and observation.error_ms
            < -early_tolerance_ms
        )
    ]

    detection_rate = len(detected) / len(observations)
    false_early_rate = (
        len(false_early) / len(observations)
    )

    if errors.size:
        median_error_ms = float(
            np.median(errors),
        )
        median_absolute_error_ms = float(
            np.median(absolute_errors),
        )
        p95_absolute_error_ms = float(
            np.percentile(
                absolute_errors,
                95,
            ),
        )
        within_150ms_rate = float(
            np.mean(absolute_errors <= 150),
        )
        within_300ms_rate = float(
            np.mean(absolute_errors <= 300),
        )
    else:
        median_error_ms = None
        median_absolute_error_ms = None
        p95_absolute_error_ms = None
        within_150ms_rate = 0.0
        within_300ms_rate = 0.0

    passed = (
        detection_rate >= 0.98
        and false_early_rate <= 0.01
        and median_absolute_error_ms is not None
        and median_absolute_error_ms <= 150
        and p95_absolute_error_ms is not None
        and p95_absolute_error_ms <= 300
    )

    worst_examples = sorted(
        detected,
        key=lambda observation: abs(
            observation.error_ms or 0,
        ),
        reverse=True,
    )[:20]

    return {
        "candidate_id": candidate.identifier,
        "threshold": candidate.threshold,
        "min_speech_duration_ms": (
            candidate.min_speech_duration_ms
        ),
        "min_silence_duration_ms": (
            candidate.min_silence_duration_ms
        ),
        "speech_pad_ms": candidate.speech_pad_ms,
        "sample_count": len(observations),
        "detected_count": len(detected),
        "missed_count": len(missed),
        "detection_rate": round(
            detection_rate,
            6,
        ),
        "false_early_count": len(false_early),
        "false_early_rate": round(
            false_early_rate,
            6,
        ),
        "median_error_ms": _round_optional(
            median_error_ms,
        ),
        "median_absolute_error_ms": (
            _round_optional(
                median_absolute_error_ms,
            )
        ),
        "p95_absolute_error_ms": (
            _round_optional(
                p95_absolute_error_ms,
            )
        ),
        "within_150ms_rate": round(
            within_150ms_rate,
            6,
        ),
        "within_300ms_rate": round(
            within_300ms_rate,
            6,
        ),
        "passed": passed,
        "missed_examples": [
            observation.filename
            for observation in missed[:20]
        ],
        "worst_examples": [
            {
                "filename": observation.filename,
                "expected_onset_ms": (
                    observation.expected_onset_ms
                ),
                "detected_onset_ms": (
                    observation.detected_onset_ms
                ),
                "error_ms": observation.error_ms,
            }
            for observation in worst_examples
        ],
    }


def choose_best_candidate(
    summaries: list[dict[str, Any]],
) -> dict[str, Any]:
    if not summaries:
        raise ValueError(
            "VAD 후보 요약이 하나 이상 필요합니다.",
        )

    def ranking_key(
        summary: dict[str, Any],
    ) -> tuple:
        median_error = (
            summary["median_absolute_error_ms"]
        )
        p95_error = summary["p95_absolute_error_ms"]

        return (
            not summary["passed"],
            -summary["detection_rate"],
            summary["false_early_rate"],
            (
                float("inf")
                if median_error is None
                else median_error
            ),
            (
                float("inf")
                if p95_error is None
                else p95_error
            ),
        )

    return min(
        summaries,
        key=ranking_key,
    )


def _round_optional(
    value: float | None,
) -> float | None:
    if value is None:
        return None

    return round(value, 3)