import numpy as np
import pytest

from app.inference.artifacts import (
    EXPECTED_FUSION_FEATURE_ORDER,
)
from app.inference.fusion import (
    FusionFeatures,
    FusionInferenceError,
    FusionInferenceService,
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
    assert result.decision_threshold == 0.5
    assert (
        result.threshold_version
        == "fusion-threshold-v1"
    )
    assert result.risk_flag is True
    assert result.features == features


def test_threshold_is_inclusive() -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=0.5,
        ),
    )

    result = service.infer(
        _valid_features(),
    )

    assert result.risk_flag is True


def test_score_below_threshold_is_not_flagged() -> None:
    service = _service(
        FakeFusionPipeline(
            model_score=0.4999,
        ),
    )

    result = service.infer(
        _valid_features(),
    )

    assert result.risk_flag is False


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
            decision_threshold=0.5,
            threshold_version=(
                "fusion-threshold-v1"
            ),
            model_version="test-fusion-v1",
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
        decision_threshold=0.5,
        threshold_version=(
            "fusion-threshold-v1"
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