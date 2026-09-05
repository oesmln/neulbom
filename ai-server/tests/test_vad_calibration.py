import pytest

from app.audio.vad_calibration import (
    VadAuditObservation,
    VadCandidate,
    VadObservation,
    calculate_delay_feature_drift,
    choose_best_candidate,
    summarize_candidate,
)


CORE_CATEGORIES = ("지남력", "기억력", "주의력", "유창성")
NONSPEECH_AUDIT = [
    VadAuditObservation(filename="silence", detected=False),
]


def test_summarizes_raw_and_bias_corrected_errors() -> None:
    candidate = VadCandidate(
        threshold=0.5,
        min_speech_duration_ms=100,
    )
    observations = [
        VadObservation(
            filename="first",
            expected_onset_ms=1000,
            detected_onset_ms=1050,
        ),
        VadObservation(
            filename="second",
            expected_onset_ms=1000,
            detected_onset_ms=800,
        ),
        VadObservation(
            filename="third",
            expected_onset_ms=1000,
            detected_onset_ms=None,
        ),
    ]

    summary = summarize_candidate(
        candidate=candidate,
        observations=observations,
    )

    assert summary["sample_count"] == 3
    assert summary["detected_count"] == 2
    assert summary["missed_count"] == 1
    assert summary["detection_rate"] == pytest.approx(2 / 3, abs=1e-6)
    assert summary["onset_bias_correction_ms"] == -75
    assert summary["raw_false_early_count"] == 1
    assert summary["corrected_false_early_count"] == 1
    assert summary["raw_error_metrics"][
        "median_absolute_error_ms"
    ] == 125.0
    assert summary["corrected_error_metrics"][
        "median_absolute_error_ms"
    ] == 125.0
    assert summary["passed"] is False


def test_candidate_passes_selection_criteria() -> None:
    summary = summarize_candidate(
        candidate=VadCandidate(
            threshold=0.35,
            min_speech_duration_ms=100,
        ),
        observations=_complete_subject_observations(error_ms=50),
        synthetic_nonspeech_audit=NONSPEECH_AUDIT,
    )

    assert summary["detection_rate"] == 1.0
    assert summary["onset_bias_correction_ms"] == 50
    assert summary["corrected_false_early_rate"] == 0.0
    assert summary["corrected_error_metrics"][
        "p95_absolute_error_ms"
    ] == 0.0
    assert summary["delay_feature_drift"]["subject_count"] == 1
    assert summary["passed"] is True


def test_synthetic_nonspeech_detection_rejects_candidate() -> None:
    summary = summarize_candidate(
        candidate=VadCandidate(
            threshold=0.2,
            min_speech_duration_ms=64,
        ),
        observations=_complete_subject_observations(error_ms=50),
        empty_stt_audit=[
            VadAuditObservation(filename="empty-stt", detected=True),
        ],
        synthetic_nonspeech_audit=[
            VadAuditObservation(filename="silence", detected=False),
            VadAuditObservation(filename="noise", detected=True),
        ],
    )

    assert summary["empty_stt_audit"]["detection_rate"] == 1.0
    assert summary["synthetic_nonspeech_audit"][
        "detection_rate"
    ] == 0.5
    assert summary["synthetic_nonspeech_audit"][
        "undetected_examples"
    ] == ["silence"]
    assert summary["passed"] is False


def test_calculates_subject_level_fusion_delay_drift() -> None:
    observations = [
        VadObservation(
            filename=f"clip-{category}",
            expected_onset_ms=1000,
            detected_onset_ms=1100,
            subject_id="P001",
            category=category,
            manual_response_delay_ms=1000,
        )
        for category in CORE_CATEGORIES
    ]

    drift = calculate_delay_feature_drift(
        observations=observations,
        onset_bias_correction_ms=50,
    )

    assert drift["subject_count"] == 1
    assert drift["mean_difference_seconds"] == 0.05
    assert drift["p95_absolute_difference_seconds"] == 0.05


def test_selects_passing_candidate_first() -> None:
    passing = summarize_candidate(
        candidate=VadCandidate(
            threshold=0.35,
            min_speech_duration_ms=100,
        ),
        observations=_complete_subject_observations(error_ms=50),
        synthetic_nonspeech_audit=NONSPEECH_AUDIT,
    )
    failing_observations = _complete_subject_observations(error_ms=50)
    failing_observations[0] = VadObservation(
        filename="missed",
        expected_onset_ms=1000,
        detected_onset_ms=None,
        subject_id="P001",
        category="지남력",
        manual_response_delay_ms=1000,
    )
    failing = summarize_candidate(
        candidate=VadCandidate(
            threshold=0.5,
            min_speech_duration_ms=100,
        ),
        observations=failing_observations,
        synthetic_nonspeech_audit=NONSPEECH_AUDIT,
    )

    selected = choose_best_candidate([failing, passing])

    assert selected["candidate_id"] == passing["candidate_id"]


def test_prefers_longer_minimum_speech_when_metrics_are_equal() -> None:
    observations = _complete_subject_observations(error_ms=50)
    shorter = summarize_candidate(
        candidate=VadCandidate(
            threshold=0.2,
            min_speech_duration_ms=64,
        ),
        observations=observations,
        synthetic_nonspeech_audit=NONSPEECH_AUDIT,
    )
    longer = summarize_candidate(
        candidate=VadCandidate(
            threshold=0.2,
            min_speech_duration_ms=100,
        ),
        observations=observations,
        synthetic_nonspeech_audit=NONSPEECH_AUDIT,
    )

    selected = choose_best_candidate([shorter, longer])

    assert selected["min_speech_duration_ms"] == 100


def test_rejects_empty_observations() -> None:
    with pytest.raises(ValueError, match="하나 이상"):
        summarize_candidate(
            candidate=VadCandidate(
                threshold=0.5,
                min_speech_duration_ms=100,
            ),
            observations=[],
        )


def _complete_subject_observations(
    *,
    error_ms: int,
) -> list[VadObservation]:
    return [
        VadObservation(
            filename=f"clip-{category}-{index}",
            expected_onset_ms=1000,
            detected_onset_ms=1000 + error_ms,
            subject_id="P001",
            category=category,
            manual_response_delay_ms=1000,
        )
        for category in CORE_CATEGORIES
        for index in range(25)
    ]
