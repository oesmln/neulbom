from collections import defaultdict
from dataclasses import dataclass
from typing import Any, Iterable

import numpy as np


CORE_CATEGORIES = frozenset(
    {"지남력", "기억력", "주의력", "유창성"},
)


@dataclass(frozen=True, slots=True)
class VadCandidate:
    threshold: float
    min_speech_duration_ms: int
    min_silence_duration_ms: int = 100
    speech_pad_ms: int = 0

    @property
    def identifier(self) -> str:
        threshold_text = str(self.threshold).replace(".", "_")
        return (
            f"threshold_{threshold_text}"
            f"_speech_{self.min_speech_duration_ms}ms"
        )


@dataclass(frozen=True, slots=True)
class VadObservation:
    filename: str
    expected_onset_ms: int
    detected_onset_ms: int | None
    subject_id: str = ""
    category: str = ""
    manual_response_delay_ms: int = 0

    @property
    def error_ms(self) -> int | None:
        if self.detected_onset_ms is None:
            return None
        return self.detected_onset_ms - self.expected_onset_ms


@dataclass(frozen=True, slots=True)
class VadAuditObservation:
    filename: str
    detected: bool


def summarize_candidate(
    *,
    candidate: VadCandidate,
    observations: list[VadObservation],
    empty_stt_audit: Iterable[VadAuditObservation] = (),
    synthetic_nonspeech_audit: Iterable[VadAuditObservation] = (),
    early_tolerance_ms: int = 100,
) -> dict[str, Any]:
    if not observations:
        raise ValueError("VAD 관측값이 하나 이상 필요합니다.")

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

    raw_errors = np.asarray(
        [observation.error_ms for observation in detected],
        dtype=np.float64,
    )
    onset_bias_correction_ms = (
        int(round(float(np.median(raw_errors))))
        if raw_errors.size
        else 0
    )
    corrected_errors = raw_errors - onset_bias_correction_ms

    raw_false_early_count = int(
        np.sum(raw_errors < -early_tolerance_ms),
    )
    corrected_false_early_count = int(
        np.sum(corrected_errors < -early_tolerance_ms),
    )
    sample_count = len(observations)
    detection_rate = len(detected) / sample_count
    raw_false_early_rate = raw_false_early_count / sample_count
    corrected_false_early_rate = (
        corrected_false_early_count / sample_count
    )

    raw_metrics = _error_metrics(raw_errors)
    corrected_metrics = _error_metrics(corrected_errors)
    delay_feature_drift = calculate_delay_feature_drift(
        observations=detected,
        onset_bias_correction_ms=onset_bias_correction_ms,
    )
    empty_stt_summary = _summarize_audit(empty_stt_audit)
    synthetic_summary = _summarize_audit(
        synthetic_nonspeech_audit,
    )

    passed = (
        detection_rate >= 0.99
        and corrected_false_early_rate <= 0.01
        and corrected_metrics["median_absolute_error_ms"]
        is not None
        and corrected_metrics["median_absolute_error_ms"] <= 100
        and corrected_metrics["p95_absolute_error_ms"] is not None
        and corrected_metrics["p95_absolute_error_ms"] <= 300
        and synthetic_summary["sample_count"] > 0
        and synthetic_summary["detection_rate"] == 0.0
        and delay_feature_drift["subject_count"] > 0
        and delay_feature_drift[
            "p95_absolute_difference_seconds"
        ]
        is not None
        and delay_feature_drift[
            "p95_absolute_difference_seconds"
        ]
        <= 0.15
    )

    worst_examples = sorted(
        detected,
        key=lambda observation: abs(
            (observation.error_ms or 0)
            - onset_bias_correction_ms,
        ),
        reverse=True,
    )[:20]

    return {
        "candidate_id": candidate.identifier,
        "threshold": candidate.threshold,
        "min_speech_duration_ms": candidate.min_speech_duration_ms,
        "min_silence_duration_ms": candidate.min_silence_duration_ms,
        "speech_pad_ms": candidate.speech_pad_ms,
        "sample_count": sample_count,
        "detected_count": len(detected),
        "missed_count": len(missed),
        "detection_rate": round(detection_rate, 6),
        "onset_bias_correction_ms": onset_bias_correction_ms,
        "raw_false_early_count": raw_false_early_count,
        "raw_false_early_rate": round(raw_false_early_rate, 6),
        "corrected_false_early_count": corrected_false_early_count,
        "corrected_false_early_rate": round(
            corrected_false_early_rate,
            6,
        ),
        "raw_error_metrics": raw_metrics,
        "corrected_error_metrics": corrected_metrics,
        "delay_feature_drift": delay_feature_drift,
        "empty_stt_audit": empty_stt_summary,
        "synthetic_nonspeech_audit": synthetic_summary,
        "passed": passed,
        "missed_examples": [
            observation.filename for observation in missed[:20]
        ],
        "worst_examples": [
            {
                "filename": observation.filename,
                "expected_onset_ms": observation.expected_onset_ms,
                "detected_onset_ms": observation.detected_onset_ms,
                "raw_error_ms": observation.error_ms,
                "corrected_error_ms": (
                    (observation.error_ms or 0)
                    - onset_bias_correction_ms
                ),
            }
            for observation in worst_examples
        ],
    }


def calculate_delay_feature_drift(
    *,
    observations: Iterable[VadObservation],
    onset_bias_correction_ms: int,
) -> dict[str, Any]:
    grouped: dict[
        tuple[str, str],
        list[tuple[int, int]],
    ] = defaultdict(list)

    for observation in observations:
        if (
            observation.detected_onset_ms is None
            or not observation.subject_id
            or observation.category not in CORE_CATEGORIES
        ):
            continue

        error_ms = observation.error_ms
        if error_ms is None:
            continue

        estimated_delay_ms = max(
            0,
            observation.manual_response_delay_ms
            + error_ms
            - onset_bias_correction_ms,
        )
        grouped[(observation.subject_id, observation.category)].append(
            (
                observation.manual_response_delay_ms,
                estimated_delay_ms,
            ),
        )

    by_subject: dict[str, dict[str, tuple[float, float]]] = (
        defaultdict(dict)
    )
    for (subject_id, category), values in grouped.items():
        manual = [value[0] for value in values]
        estimated = [value[1] for value in values]
        by_subject[subject_id][category] = (
            float(np.median(manual)),
            float(np.median(estimated)),
        )

    differences_seconds: list[float] = []
    for category_values in by_subject.values():
        if not CORE_CATEGORIES.issubset(category_values):
            continue
        manual_feature_ms = float(
            np.mean(
                [
                    category_values[category][0]
                    for category in CORE_CATEGORIES
                ],
            ),
        )
        estimated_feature_ms = float(
            np.mean(
                [
                    category_values[category][1]
                    for category in CORE_CATEGORIES
                ],
            ),
        )
        differences_seconds.append(
            (estimated_feature_ms - manual_feature_ms) / 1000,
        )

    if not differences_seconds:
        return {
            "subset": "successful_stt_and_vad_detected",
            "subject_count": 0,
            "mean_difference_seconds": None,
            "median_absolute_difference_seconds": None,
            "p95_absolute_difference_seconds": None,
            "maximum_absolute_difference_seconds": None,
        }

    differences = np.asarray(differences_seconds, dtype=np.float64)
    absolute_differences = np.abs(differences)
    return {
        "subset": "successful_stt_and_vad_detected",
        "subject_count": len(differences_seconds),
        "mean_difference_seconds": _round_optional(
            float(np.mean(differences)),
            digits=6,
        ),
        "median_absolute_difference_seconds": _round_optional(
            float(np.median(absolute_differences)),
            digits=6,
        ),
        "p95_absolute_difference_seconds": _round_optional(
            float(np.percentile(absolute_differences, 95)),
            digits=6,
        ),
        "maximum_absolute_difference_seconds": _round_optional(
            float(np.max(absolute_differences)),
            digits=6,
        ),
    }


def choose_best_candidate(
    summaries: list[dict[str, Any]],
) -> dict[str, Any]:
    if not summaries:
        raise ValueError("VAD 후보 요약이 하나 이상 필요합니다.")

    def ranking_key(summary: dict[str, Any]) -> tuple:
        corrected = summary["corrected_error_metrics"]
        drift = summary["delay_feature_drift"]
        return (
            not summary["passed"],
            summary["synthetic_nonspeech_audit"]["detection_rate"],
            -summary["detection_rate"],
            summary["corrected_false_early_rate"],
            _none_as_infinity(corrected["p95_absolute_error_ms"]),
            _none_as_infinity(
                drift["p95_absolute_difference_seconds"],
            ),
            _none_as_infinity(corrected["median_absolute_error_ms"]),
            -summary["min_speech_duration_ms"],
        )

    return min(summaries, key=ranking_key)


def _error_metrics(errors: np.ndarray) -> dict[str, float | None]:
    if not errors.size:
        return {
            "median_error_ms": None,
            "median_absolute_error_ms": None,
            "p95_absolute_error_ms": None,
            "within_150ms_rate": 0.0,
            "within_300ms_rate": 0.0,
        }

    absolute_errors = np.abs(errors)
    return {
        "median_error_ms": _round_optional(float(np.median(errors))),
        "median_absolute_error_ms": _round_optional(
            float(np.median(absolute_errors)),
        ),
        "p95_absolute_error_ms": _round_optional(
            float(np.percentile(absolute_errors, 95)),
        ),
        "within_150ms_rate": round(
            float(np.mean(absolute_errors <= 150)),
            6,
        ),
        "within_300ms_rate": round(
            float(np.mean(absolute_errors <= 300)),
            6,
        ),
    }


def _summarize_audit(
    observations: Iterable[VadAuditObservation],
) -> dict[str, Any]:
    values = list(observations)
    detected = [observation for observation in values if observation.detected]
    undetected = [
        observation for observation in values if not observation.detected
    ]
    return {
        "sample_count": len(values),
        "detected_count": len(detected),
        "undetected_count": len(undetected),
        "detection_rate": (
            round(len(detected) / len(values), 6) if values else 0.0
        ),
        "detected_examples": [
            observation.filename for observation in detected[:20]
        ],
        "undetected_examples": [
            observation.filename for observation in undetected[:20]
        ],
    }


def _none_as_infinity(value: float | None) -> float:
    return float("inf") if value is None else value


def _round_optional(
    value: float | None,
    *,
    digits: int = 3,
) -> float | None:
    if value is None:
        return None
    return round(value, digits)
