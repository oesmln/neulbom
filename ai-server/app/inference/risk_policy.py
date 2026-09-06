import json
from dataclasses import dataclass
from enum import Enum
from json import JSONDecodeError
from math import isfinite
from pathlib import Path
from typing import Any


EXPECTED_SCHEMA_VERSION = (
    "fusion-threshold-policy-schema-v1"
)
EXPECTED_THRESHOLD_VERSION = (
    "fusion-threshold-v2"
)
EXPECTED_SCREENING_THRESHOLD = 0.461
EXPECTED_REVIEW_THRESHOLD = 0.802


class RiskThresholdPolicyError(ValueError):
    """위험 단계 운영 정책이 올바르지 않을 때 발생한다."""


class RiskLevel(str, Enum):
    STABLE = "stable"
    MONITORING_NEEDED = "monitoring_needed"
    REVIEW_NEEDED = "review_needed"


EXPECTED_RISK_LEVELS = tuple(
    level.value
    for level in RiskLevel
)


@dataclass(frozen=True, slots=True)
class RiskThresholdPolicy:
    schema_version: str
    threshold_version: str
    screening_threshold: float
    review_threshold: float

    @classmethod
    def from_path(
        cls,
        path: Path,
    ) -> "RiskThresholdPolicy":
        payload = _load_json(path)

        schema_version = _require_string(
            payload,
            "schema_version",
        )
        threshold_version = _require_string(
            payload,
            "threshold_version",
        )
        screening_threshold = _require_number(
            payload,
            "screening_threshold",
        )
        review_threshold = _require_number(
            payload,
            "review_threshold",
        )
        risk_levels = _require_string_list(
            payload,
            "risk_levels",
        )

        if (
            schema_version
            != EXPECTED_SCHEMA_VERSION
        ):
            raise RiskThresholdPolicyError(
                "지원하지 않는 위험 정책 "
                "schema_version입니다.",
            )

        if (
            threshold_version
            != EXPECTED_THRESHOLD_VERSION
        ):
            raise RiskThresholdPolicyError(
                "지원하지 않는 "
                "threshold_version입니다.",
            )

        if (
            screening_threshold
            != EXPECTED_SCREENING_THRESHOLD
        ):
            raise RiskThresholdPolicyError(
                "screening_threshold는 "
                "0.461이어야 합니다.",
            )

        if (
            review_threshold
            != EXPECTED_REVIEW_THRESHOLD
        ):
            raise RiskThresholdPolicyError(
                "review_threshold는 "
                "0.802여야 합니다.",
            )

        if risk_levels != EXPECTED_RISK_LEVELS:
            raise RiskThresholdPolicyError(
                "risk_levels가 운영 계약과 "
                "일치하지 않습니다.",
            )

        if not (
            0.0
            <= screening_threshold
            < review_threshold
            <= 1.0
        ):
            raise RiskThresholdPolicyError(
                "위험 임계값은 "
                "0 <= screening < review <= 1 "
                "조건을 만족해야 합니다.",
            )

        return cls(
            schema_version=schema_version,
            threshold_version=threshold_version,
            screening_threshold=(
                screening_threshold
            ),
            review_threshold=review_threshold,
        )

    def classify(
        self,
        model_score: float,
    ) -> RiskLevel:
        score = self._validate_score(
            model_score,
        )

        if score < self.screening_threshold:
            return RiskLevel.STABLE

        if score < self.review_threshold:
            return RiskLevel.MONITORING_NEEDED

        return RiskLevel.REVIEW_NEEDED

    def is_risk_flagged(
        self,
        model_score: float,
    ) -> bool:
        score = self._validate_score(
            model_score,
        )

        return score >= self.screening_threshold

    @staticmethod
    def _validate_score(
        model_score: float,
    ) -> float:
        if (
            isinstance(model_score, bool)
            or not isinstance(
                model_score,
                (int, float),
            )
        ):
            raise ValueError(
                "model_score는 숫자여야 합니다.",
            )

        score = float(model_score)

        if not (
            isfinite(score)
            and 0.0 <= score <= 1.0
        ):
            raise ValueError(
                "model_score는 0 이상 1 이하의 "
                "유한한 숫자여야 합니다.",
            )

        return score


def _load_json(
    path: Path,
) -> dict[str, Any]:
    try:
        payload = json.loads(
            path.read_text(
                encoding="utf-8",
            ),
        )
    except OSError as error:
        raise RiskThresholdPolicyError(
            "위험 정책 파일을 읽지 "
            "못했습니다.",
        ) from error
    except (
        UnicodeDecodeError,
        JSONDecodeError,
    ) as error:
        raise RiskThresholdPolicyError(
            "위험 정책 파일이 올바른 "
            "UTF-8 JSON이 아닙니다.",
        ) from error

    if not isinstance(payload, dict):
        raise RiskThresholdPolicyError(
            "위험 정책의 최상위 값은 "
            "객체여야 합니다.",
        )

    return payload


def _require_string(
    payload: dict[str, Any],
    key: str,
) -> str:
    value = payload.get(key)

    if (
        not isinstance(value, str)
        or not value.strip()
    ):
        raise RiskThresholdPolicyError(
            f"{key}는 비어 있지 않은 "
            "문자열이어야 합니다.",
        )

    return value


def _require_number(
    payload: dict[str, Any],
    key: str,
) -> float:
    value = payload.get(key)

    if (
        isinstance(value, bool)
        or not isinstance(value, (int, float))
    ):
        raise RiskThresholdPolicyError(
            f"{key}는 숫자여야 합니다.",
        )

    result = float(value)

    if not isfinite(result):
        raise RiskThresholdPolicyError(
            f"{key}는 유한한 숫자여야 합니다.",
        )

    return result


def _require_string_list(
    payload: dict[str, Any],
    key: str,
) -> tuple[str, ...]:
    value = payload.get(key)

    if (
        not isinstance(value, list)
        or not all(
            isinstance(item, str)
            for item in value
        )
    ):
        raise RiskThresholdPolicyError(
            f"{key}는 문자열 배열이어야 합니다.",
        )

    return tuple(value)