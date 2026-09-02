import pytest

from app.audio.vad_calibration import (
    VadCandidate,
    VadObservation,
    choose_best_candidate,
    summarize_candidate,
)


def test_summarizes_vad_candidate() -> None:
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
    assert summary["detection_rate"] == pytest.approx(
        2 / 3,
        abs=1e-6,
    )
    assert summary["false_early_count"] == 1
    assert summary["median_absolute_error_ms"] == (
        125.0
    )
    assert summary["passed"] is False


def test_candidate_passes_selection_criteria() -> None:
    candidate = VadCandidate(
        threshold=0.5,
        min_speech_duration_ms=100,
    )
    observations = [
        VadObservation(
            filename=f"clip-{index}",
            expected_onset_ms=1000,
            detected_onset_ms=1050,
        )
        for index in range(100)
    ]

    summary = summarize_candidate(
        candidate=candidate,
        observations=observations,
    )

    assert summary["detection_rate"] == 1.0
    assert summary["false_early_rate"] == 0.0
    assert summary["passed"] is True


def test_selects_passing_candidate_first() -> None:
    passing = {
        "candidate_id": "passing",
        "passed": True,
        "detection_rate": 0.98,
        "false_early_rate": 0.0,
        "median_absolute_error_ms": 100.0,
        "p95_absolute_error_ms": 250.0,
    }
    failing = {
        "candidate_id": "failing",
        "passed": False,
        "detection_rate": 1.0,
        "false_early_rate": 0.0,
        "median_absolute_error_ms": 50.0,
        "p95_absolute_error_ms": 100.0,
    }

    selected = choose_best_candidate(
        [failing, passing],
    )

    assert selected["candidate_id"] == "passing"


def test_rejects_empty_observations() -> None:
    with pytest.raises(
        ValueError,
        match="하나 이상",
    ):
        summarize_candidate(
            candidate=VadCandidate(
                threshold=0.5,
                min_speech_duration_ms=100,
            ),
            observations=[],
        )