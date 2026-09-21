from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pytest

from app.inference.artifacts import (
    EXPECTED_FUSION_FEATURE_ORDER,
)
from app.inference.fusion import (
    FusionFeatures,
    FusionInferenceError,
    FusionInferenceService,
    _validate_loaded_pipeline,
)
from app.inference.risk_policy import (
    EXPECTED_REVIEW_THRESHOLD,
    EXPECTED_SCREENING_THRESHOLD,
    RiskLevel,
    RiskThresholdPolicy,
)

PROJECT_ROOT = (
    Path(__file__).resolve().parents[1]
)
POLICY_PATH = (
    PROJECT_ROOT
    / "configs"
    / "fusion-threshold-v2.json"
)


class FakeFusionPipeline:
    def __init__(
        self,
        model_score: float,
    ) -> None:
        self._model_score = model_score
        self.received_frame = None

    def predict_proba(self, frame):
        self.received_frame = frame.copy()

        return np.asarray(
            [
                [
                    1.0 - self._model_score,
                    self._model_score,
                ],
            ],
            dtype=np.float64,
        )


def test_runs_fusion_with_exact_feature_order() -> None:
    pipeline = FakeFusionPipeline(
        model_score=0.7,
    )
    service = _service(pipeline)

    features = FusionFeatures(
        ast_logit=1.2,
        kcelectra_logit=-0.3,
        category_balanced_wrong_event_score=(
            0.375
        ),
        category_balanced_median_delay=0.8,
    )

    result = service.infer(features)

    assert list(
        pipeline.received_frame.columns,
    ) == list(
        EXPECTED_FUSION_FEATURE_ORDER,
    )
    assert (
        pipeline.received_frame.iloc[0].tolist()
        == pytest.approx(
            [
                1.2,
                -0.3,
                0.375,
                0.8,
            ],
        )
    )

    assert result.model_score == (
        pytest.approx(0.7)
    )
    assert result.decision_threshold == (
        EXPECTED_SCREENING_THRESHOLD
    )
    assert result.review_threshold == (
        EXPECTED_REVIEW_THRESHOLD
    )
    assert (
        result.threshold_version
        == "fusion-threshold-v2"
    )
    assert result.risk_flag is True
    assert (
        result.risk_level
        == RiskLevel.MONITORING_NEEDED
    )
    assert result.features == features


def test_screening_threshold_is_inclusive() -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=(
                EXPECTED_SCREENING_THRESHOLD
            ),
        ),
    )

    result = service.infer(
        _valid_features(),
    )

    assert result.risk_flag is True
    assert (
        result.risk_level
        == RiskLevel.MONITORING_NEEDED
    )


def test_score_below_screening_threshold_is_stable(
) -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=(
                EXPECTED_SCREENING_THRESHOLD
                - 1e-12
            ),
        ),
    )

    result = service.infer(
        _valid_features(),
    )

    assert result.risk_flag is False
    assert (
        result.risk_level
        == RiskLevel.STABLE
    )


def test_review_threshold_is_inclusive() -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=EXPECTED_REVIEW_THRESHOLD,
        ),
    )

    result = service.infer(
        _valid_features(),
    )

    assert result.risk_flag is True
    assert (
        result.risk_level
        == RiskLevel.REVIEW_NEEDED
    )


def test_score_below_review_threshold_needs_monitoring(
) -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=(
                EXPECTED_REVIEW_THRESHOLD - 1e-12
            ),
        ),
    )

    result = service.infer(
        _valid_features(),
    )

    assert result.risk_flag is True
    assert (
        result.risk_level
        == RiskLevel.MONITORING_NEEDED
    )


def test_rejects_wrong_event_score_out_of_range() -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=0.7,
        ),
    )

    with pytest.raises(
        ValueError,
        match="0 이상 1 이하",
    ):
        service.infer(
            FusionFeatures(
                ast_logit=0.1,
                kcelectra_logit=0.2,
                category_balanced_wrong_event_score=(
                    1.1
                ),
                category_balanced_median_delay=0.5,
            ),
        )


def test_rejects_negative_delay() -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=0.7,
        ),
    )

    with pytest.raises(
        ValueError,
        match="delay",
    ):
        service.infer(
            FusionFeatures(
                ast_logit=0.1,
                kcelectra_logit=0.2,
                category_balanced_wrong_event_score=(
                    0.5
                ),
                category_balanced_median_delay=-0.1,
            ),
        )


def test_rejects_non_finite_feature() -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=0.7,
        ),
    )

    with pytest.raises(
        ValueError,
        match="유한한",
    ):
        service.infer(
            FusionFeatures(
                ast_logit=float("nan"),
                kcelectra_logit=0.2,
                category_balanced_wrong_event_score=(
                    0.5
                ),
                category_balanced_median_delay=0.1,
            ),
        )


def test_rejects_invalid_probability_output() -> None:
    pipeline = FakeFusionPipeline(
        model_score=float("nan"),
    )
    service = _service(pipeline)

    with pytest.raises(
        FusionInferenceError,
        match="model_score",
    ):
        service.infer(
            _valid_features(),
        )


def test_rejects_wrong_feature_order() -> None:
    with pytest.raises(
        ValueError,
        match="feature 순서",
    ):
        FusionInferenceService(
            pipeline=FakeFusionPipeline(
                model_score=0.7,
            ),
            feature_order=tuple(
                reversed(
                    EXPECTED_FUSION_FEATURE_ORDER,
                ),
            ),
            class_order=(0, 1),
            risk_policy=(
                RiskThresholdPolicy.from_path(
                    POLICY_PATH,
                )
            ),
            model_version="test-fusion-v1",
        )


def test_validates_standardized_fusion_contract_values(
) -> None:
    feature_order = EXPECTED_FUSION_FEATURE_ORDER
    class_order = (0, 1)
    scaler_mean = np.asarray(
        [0.3, -0.1, 0.4, 0.8],
        dtype=np.float64,
    )
    scaler_scale = np.asarray(
        [1.3, 0.07, 0.27, 0.42],
        dtype=np.float64,
    )
    coefficient = np.asarray(
        [[-0.1, 0.5, 1.0, 0.4]],
        dtype=np.float64,
    )
    intercept = np.asarray(
        [-0.05],
        dtype=np.float64,
    )
    pipeline = SimpleNamespace(
        feature_names_in_=np.asarray(feature_order),
        classes_=np.asarray(class_order),
        n_features_in_=len(feature_order),
        named_steps={
            "scaler": SimpleNamespace(
                mean_=scaler_mean,
                scale_=scaler_scale,
            ),
            "lr": SimpleNamespace(
                coef_=coefficient,
                intercept_=intercept,
            ),
        },
    )
    contract = {
        "scaler_mean": scaler_mean.tolist(),
        "scaler_scale": scaler_scale.tolist(),
        "lr_coef_standardized": (
            coefficient[0].tolist()
        ),
        "lr_intercept_standardized": (
            intercept.tolist()
        ),
    }

    _validate_loaded_pipeline(
        pipeline=pipeline,
        contract=contract,
        feature_order=feature_order,
        class_order=class_order,
    )


def _service(
    pipeline: FakeFusionPipeline,
) -> FusionInferenceService:
    return FusionInferenceService(
        pipeline=pipeline,
        feature_order=(
            EXPECTED_FUSION_FEATURE_ORDER
        ),
        class_order=(0, 1),
        risk_policy=(
            RiskThresholdPolicy.from_path(
                POLICY_PATH,
            )
        ),
        model_version="test-fusion-v1",
    )


def _valid_features() -> FusionFeatures:
    return FusionFeatures(
        ast_logit=0.1,
        kcelectra_logit=0.2,
        category_balanced_wrong_event_score=(
            0.5
        ),
        category_balanced_median_delay=0.7,
    )
