import json
from dataclasses import dataclass
from json import JSONDecodeError
from math import isfinite
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
import sklearn

from app.inference.artifacts import (
    EXPECTED_FUSION_FEATURE_ORDER,
    ModelArtifactBundle,
)
from app.inference.risk_policy import (
    EXPECTED_THRESHOLD_VERSION,
    RiskLevel,
    RiskThresholdPolicy,
)

FUSION_THRESHOLD_VERSION = (
    EXPECTED_THRESHOLD_VERSION
)
DEFAULT_RISK_POLICY_PATH = (
    Path(__file__).resolve().parents[2]
    / "configs"
    / "fusion-threshold-v2.json"
)
EXPECTED_SKLEARN_VERSION = "1.6.1"


class FusionInferenceError(RuntimeError):
    """융합 모델 로딩 또는 추론에 실패한 경우 발생한다."""


@dataclass(frozen=True, slots=True)
class FusionFeatures:
    ast_logit: float
    kcelectra_logit: float
    category_balanced_wrong_event_score: float
    category_balanced_median_delay: float


@dataclass(frozen=True, slots=True)
class FusionInferenceResult:
    model_version: str
    model_score: float
    decision_threshold: float
    review_threshold: float
    threshold_version: str
    risk_flag: bool
    risk_level: RiskLevel
    features: FusionFeatures


class FusionInferenceService:
    def __init__(
        self,
        *,
        pipeline: Any,
        feature_order: tuple[str, ...],
        class_order: tuple[int, ...],
        risk_policy: RiskThresholdPolicy,
        model_version: str,
    ) -> None:
        if (
            feature_order
            != EXPECTED_FUSION_FEATURE_ORDER
        ):
            raise ValueError(
                "fusion feature 순서가 계약과 "
                "일치하지 않습니다.",
            )

        if class_order != (0, 1):
            raise ValueError(
                "fusion class 순서는 "
                "(0, 1)이어야 합니다.",
            )

        if not isinstance(
            risk_policy,
            RiskThresholdPolicy,
        ):
            raise ValueError(
                "risk_policy가 필요합니다.",
            )

        if not model_version.strip():
            raise ValueError(
                "fusion model_version이 필요합니다.",
            )

        if not callable(
            getattr(
                pipeline,
                "predict_proba",
                None,
            ),
        ):
            raise ValueError(
                "fusion pipeline에 predict_proba가 "
                "없습니다.",
            )

        self._pipeline = pipeline
        self._feature_order = feature_order
        self._class_order = class_order
        self._risk_policy = risk_policy
        self._model_version = model_version

    @classmethod
    def from_artifacts(
        cls,
        artifacts: ModelArtifactBundle,
        *,
        risk_policy_path: Path = (
            DEFAULT_RISK_POLICY_PATH
        ),
    ) -> "FusionInferenceService":
        if (
            sklearn.__version__
            != EXPECTED_SKLEARN_VERSION
        ):
            raise FusionInferenceError(
                "fusion artifact와 scikit-learn "
                "버전이 일치하지 않습니다: "
                f"runtime={sklearn.__version__}, "
                f"required={EXPECTED_SKLEARN_VERSION}",
            )

        contract = _load_contract(
            artifacts.fusion_contract_path,
        )

        try:
            pipeline = joblib.load(
                artifacts.fusion_pipeline_path,
            )
        except Exception as error:
            raise FusionInferenceError(
                "fusion pipeline을 "
                "로딩하지 못했습니다.",
            ) from error

        feature_order = tuple(
            contract.get(
                "feature_order",
                (),
            ),
        )
        class_order = tuple(
            contract.get(
                "class_order",
                (),
            ),
        )

        _validate_loaded_pipeline(
            pipeline=pipeline,
            contract=contract,
            feature_order=feature_order,
            class_order=class_order,
        )

        return cls(
            pipeline=pipeline,
            feature_order=feature_order,
            class_order=class_order,
            risk_policy=(
                RiskThresholdPolicy.from_path(
                    risk_policy_path,
                )
            ),
            model_version=(
                artifacts.fusion_directory.name
            ),
        )

    def infer(
        self,
        features: FusionFeatures,
    ) -> FusionInferenceResult:
        self._validate_features(features)

        feature_values = {
            "ast_oof_logit": (
                features.ast_logit
            ),
            "kcelectra_oof_logit": (
                features.kcelectra_logit
            ),
            "category_balanced_wrong_event_score": (
                features
                .category_balanced_wrong_event_score
            ),
            "category_balanced_median_delay": (
                features
                .category_balanced_median_delay
            ),
        }

        frame = pd.DataFrame(
            [
                [
                    feature_values[name]
                    for name in self._feature_order
                ],
            ],
            columns=list(
                self._feature_order,
            ),
            dtype=float,
        )

        try:
            probabilities = (
                self._pipeline.predict_proba(
                    frame,
                )
            )
        except Exception as error:
            raise FusionInferenceError(
                "fusion pipeline 추론에 "
                "실패했습니다.",
            ) from error

        probability_array = np.asarray(
            probabilities,
            dtype=np.float64,
        )

        if probability_array.shape != (
            1,
            len(self._class_order),
        ):
            raise FusionInferenceError(
                "fusion probability 출력 크기가 "
                "올바르지 않습니다.",
            )

        dementia_class_index = (
            self._class_order.index(1)
        )
        model_score = float(
            probability_array[
                0,
                dementia_class_index,
            ],
        )

        if not (
            isfinite(model_score)
            and 0.0 <= model_score <= 1.0
        ):
            raise FusionInferenceError(
                "fusion model_score가 "
                "유효하지 않습니다.",
            )

        risk_level = (
            self._risk_policy.classify(
                model_score,
            )
        )

        return FusionInferenceResult(
            model_version=self._model_version,
            model_score=model_score,
            decision_threshold=(
                self._risk_policy
                .screening_threshold
            ),
            review_threshold=(
                self._risk_policy.review_threshold
            ),
            threshold_version=(
                self._risk_policy.threshold_version
            ),
            risk_flag=(
                self._risk_policy.is_risk_flagged(
                    model_score,
                )
            ),
            risk_level=risk_level,
            features=features,
        )

    @staticmethod
    def _validate_features(
        features: FusionFeatures,
    ) -> None:
        values = (
            features.ast_logit,
            features.kcelectra_logit,
            features
            .category_balanced_wrong_event_score,
            features
            .category_balanced_median_delay,
        )

        if not all(
            isinstance(value, (int, float))
            and not isinstance(value, bool)
            and isfinite(float(value))
            for value in values
        ):
            raise ValueError(
                "fusion 특징은 모두 유한한 "
                "숫자여야 합니다.",
            )

        wrong_event_score = (
            features
            .category_balanced_wrong_event_score
        )

        if not 0.0 <= wrong_event_score <= 1.0:
            raise ValueError(
                "category_balanced_wrong_event_score는 "
                "0 이상 1 이하여야 합니다.",
            )

        if (
            features
            .category_balanced_median_delay
            < 0.0
        ):
            raise ValueError(
                "category_balanced_median_delay는 "
                "0 이상이어야 합니다.",
            )


def _load_contract(
    path,
) -> dict[str, Any]:
    try:
        content = path.read_text(
            encoding="utf-8",
        )
        contract = json.loads(content)
    except OSError as error:
        raise FusionInferenceError(
            "fusion 계약 파일을 "
            "읽지 못했습니다.",
        ) from error
    except (
        UnicodeDecodeError,
        JSONDecodeError,
    ) as error:
        raise FusionInferenceError(
            "fusion 계약 파일이 "
            "올바른 JSON이 아닙니다.",
        ) from error

    if not isinstance(contract, dict):
        raise FusionInferenceError(
            "fusion 계약의 최상위 값은 "
            "객체여야 합니다.",
        )

    return contract


def _validate_loaded_pipeline(
    *,
    pipeline: Any,
    contract: dict[str, Any],
    feature_order: tuple[str, ...],
    class_order: tuple[int, ...],
) -> None:
    pipeline_features = tuple(
        str(value)
        for value in getattr(
            pipeline,
            "feature_names_in_",
            (),
        )
    )

    if pipeline_features != feature_order:
        raise FusionInferenceError(
            "pipeline feature 순서가 "
            "fusion 계약과 일치하지 않습니다.",
        )

    pipeline_classes = tuple(
        int(value)
        for value in getattr(
            pipeline,
            "classes_",
            (),
        )
    )

    if pipeline_classes != class_order:
        raise FusionInferenceError(
            "pipeline class 순서가 "
            "fusion 계약과 일치하지 않습니다.",
        )

    if (
        getattr(
            pipeline,
            "n_features_in_",
            None,
        )
        != len(feature_order)
    ):
        raise FusionInferenceError(
            "pipeline 입력 특징 개수가 "
            "올바르지 않습니다.",
        )

    named_steps = getattr(
        pipeline,
        "named_steps",
        None,
    )

    if (
        named_steps is None
        or "scaler" not in named_steps
        or "lr" not in named_steps
    ):
        raise FusionInferenceError(
            "fusion pipeline 단계가 "
            "올바르지 않습니다.",
        )

    scaler = named_steps["scaler"]
    classifier = named_steps["lr"]

    checks = (
        (
            getattr(scaler, "mean_", None),
            contract.get("scaler_mean"),
            "scaler mean",
        ),
        (
            getattr(scaler, "scale_", None),
            contract.get("scaler_scale"),
            "scaler scale",
        ),
        (
            getattr(classifier, "coef_", None),
            [
                contract.get(
                    "lr_coef_standardized",
                ),
            ],
            "logistic regression coefficient",
        ),
        (
            getattr(
                classifier,
                "intercept_",
                None,
            ),
            contract.get(
                "lr_intercept_standardized",
            ),
            "logistic regression intercept",
        ),
    )

    for actual, expected, name in checks:
        if actual is None or expected is None:
            raise FusionInferenceError(
                f"fusion {name} 값이 없습니다.",
            )

        try:
            matches = np.allclose(
                np.asarray(
                    actual,
                    dtype=np.float64,
                ),
                np.asarray(
                    expected,
                    dtype=np.float64,
                ),
                rtol=1e-10,
                atol=1e-12,
            )
        except (TypeError, ValueError) as error:
            raise FusionInferenceError(
                f"fusion {name} 형식이 "
                "올바르지 않습니다.",
            ) from error

        if not matches:
            raise FusionInferenceError(
                f"pipeline {name}이 계약과 "
                "일치하지 않습니다.",
            )
