import json
from pathlib import Path

import pytest

from app.inference.risk_policy import (
    RiskLevel,
    RiskThresholdPolicy,
    RiskThresholdPolicyError,
)


PROJECT_ROOT = (
    Path(__file__).resolve().parents[1]
)
POLICY_PATH = (
    PROJECT_ROOT
    / "configs"
    / "fusion-threshold-v2.json"
)


def test_loads_three_level_risk_policy() -> None:
    policy = RiskThresholdPolicy.from_path(
        POLICY_PATH,
    )

    assert (
        policy.threshold_version
        == "fusion-threshold-v2"
    )
    assert (
        policy.screening_threshold
        == 0.461
    )
    assert policy.review_threshold == 0.802


@pytest.mark.parametrize(
    ("model_score", "expected_level"),
    [
        (0.0, RiskLevel.STABLE),
        (0.460999, RiskLevel.STABLE),
        (
            0.461,
            RiskLevel.MONITORING_NEEDED,
        ),
        (
            0.801999,
            RiskLevel.MONITORING_NEEDED,
        ),
        (
            0.802,
            RiskLevel.REVIEW_NEEDED,
        ),
        (
            1.0,
            RiskLevel.REVIEW_NEEDED,
        ),
    ],
)
def test_classifies_risk_level_at_boundaries(
    model_score: float,
    expected_level: RiskLevel,
) -> None:
    policy = RiskThresholdPolicy.from_path(
        POLICY_PATH,
    )

    assert (
        policy.classify(model_score)
        == expected_level
    )


def test_risk_flag_uses_screening_threshold() -> None:
    policy = RiskThresholdPolicy.from_path(
        POLICY_PATH,
    )

    assert (
        policy.is_risk_flagged(0.460999)
        is False
    )
    assert (
        policy.is_risk_flagged(0.461)
        is True
    )


@pytest.mark.parametrize(
    "model_score",
    [
        -0.001,
        1.001,
        float("nan"),
        float("inf"),
    ],
)
def test_rejects_invalid_model_score(
    model_score: float,
) -> None:
    policy = RiskThresholdPolicy.from_path(
        POLICY_PATH,
    )

    with pytest.raises(
        ValueError,
        match="model_score",
    ):
        policy.classify(model_score)


def test_rejects_wrong_threshold_version(
    tmp_path: Path,
) -> None:
    payload = _valid_payload()
    payload["threshold_version"] = (
        "fusion-threshold-v1"
    )
    path = tmp_path / "policy.json"
    _write_json(path, payload)

    with pytest.raises(
        RiskThresholdPolicyError,
        match="threshold_version",
    ):
        RiskThresholdPolicy.from_path(path)


def test_rejects_changed_threshold(
    tmp_path: Path,
) -> None:
    payload = _valid_payload()
    payload["screening_threshold"] = 0.5
    path = tmp_path / "policy.json"
    _write_json(path, payload)

    with pytest.raises(
        RiskThresholdPolicyError,
        match="screening_threshold",
    ):
        RiskThresholdPolicy.from_path(path)


def test_rejects_changed_risk_levels(
    tmp_path: Path,
) -> None:
    payload = _valid_payload()
    payload["risk_levels"] = [
        "stable",
        "review_needed",
    ]
    path = tmp_path / "policy.json"
    _write_json(path, payload)

    with pytest.raises(
        RiskThresholdPolicyError,
        match="risk_levels",
    ):
        RiskThresholdPolicy.from_path(path)


def _valid_payload() -> dict:
    return {
        "schema_version": (
            "fusion-threshold-policy-schema-v1"
        ),
        "threshold_version": (
            "fusion-threshold-v2"
        ),
        "screening_threshold": 0.461,
        "review_threshold": 0.802,
        "risk_levels": [
            "stable",
            "monitoring_needed",
            "review_needed",
        ],
    }


def _write_json(
    path: Path,
    payload: dict,
) -> None:
    path.write_text(
        json.dumps(
            payload,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )