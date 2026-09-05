from dataclasses import dataclass
from math import exp, isfinite
from typing import Any

import numpy as np
import torch
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
)

from app.contracts.models import ContractBundle
from app.inference.artifacts import (
    EXPECTED_SEEDS,
    ModelArtifactBundle,
)


class KcElectraInferenceError(RuntimeError):
    """KcELECTRA 로딩 또는 추론에 실패한 경우 발생한다."""


@dataclass(frozen=True, slots=True)
class KcElectraClipInput:
    question_code: str
    raw_transcript: str


@dataclass(frozen=True, slots=True)
class KcElectraSeedRuntime:
    seed: int
    model: Any


@dataclass(frozen=True, slots=True)
class KcElectraClipResult:
    question_code: str
    category: str
    dementia_logit: float


@dataclass(frozen=True, slots=True)
class KcElectraCategoryResult:
    category: str
    dementia_logit: float
    clip_count: int


@dataclass(frozen=True, slots=True)
class KcElectraInferenceResult:
    model_version: str
    seed_count: int
    dementia_logit: float
    dementia_probability: float
    category_results: tuple[
        KcElectraCategoryResult,
        ...,
    ]
    clip_results: tuple[
        KcElectraClipResult,
        ...,
    ]


class KcElectraInferenceService:
    def __init__(
        self,
        *,
        tokenizer: Any,
        seed_runtimes: tuple[
            KcElectraSeedRuntime,
            ...,
        ],
        question_text_map: dict[str, str],
        question_category_map: dict[str, str],
        kcelectra_question_codes: set[str],
        core_categories: tuple[str, ...],
        max_length: int,
        model_version: str,
        device: torch.device,
    ) -> None:
        actual_seeds = tuple(
            runtime.seed
            for runtime in seed_runtimes
        )

        if actual_seeds != EXPECTED_SEEDS:
            raise ValueError(
                "KcELECTRA seed 순서가 계약과 "
                "일치하지 않습니다.",
            )

        if max_length <= 0:
            raise ValueError(
                "KcELECTRA max_length는 "
                "1 이상이어야 합니다.",
            )

        if not core_categories:
            raise ValueError(
                "KcELECTRA 핵심 범주가 필요합니다.",
            )

        if (
            set(question_text_map)
            != set(question_category_map)
        ):
            raise ValueError(
                "문항 원문과 범주 매핑의 "
                "문항 코드가 일치하지 않습니다.",
            )

        if not kcelectra_question_codes:
            raise ValueError(
                "KcELECTRA 사용 문항이 필요합니다.",
            )

        if not kcelectra_question_codes.issubset(
            question_text_map,
        ):
            raise ValueError(
                "KcELECTRA 사용 문항이 CIST 문항에 "
                "포함되지 않았습니다.",
            )

        if set(
            question_category_map.values(),
        ) != set(core_categories):
            raise ValueError(
                "KcELECTRA 문항 범주가 핵심 범주와 "
                "일치하지 않습니다.",
            )

        self._tokenizer = tokenizer
        self._seed_runtimes = seed_runtimes
        self._question_text_map = dict(
            question_text_map,
        )
        self._question_category_map = dict(
            question_category_map,
        )
        self._kcelectra_question_codes = set(
            kcelectra_question_codes,
        )
        self._core_categories = core_categories
        self._max_length = max_length
        self._model_version = model_version
        self._device = device

    @classmethod
    def from_artifacts(
        cls,
        *,
        artifacts: ModelArtifactBundle,
        contracts: ContractBundle,
        device: str | torch.device | None = None,
    ) -> "KcElectraInferenceService":
        resolved_device = _resolve_device(
            device,
        )
        first_seed = artifacts.kcelectra_seeds[
            0
        ]

        try:
            tokenizer = AutoTokenizer.from_pretrained(
                first_seed.directory,
                local_files_only=True,
                use_fast=True,
            )

            seed_runtimes: list[
                KcElectraSeedRuntime
            ] = []

            for seed_artifact in (
                artifacts.kcelectra_seeds
            ):
                model = (
                    AutoModelForSequenceClassification
                    .from_pretrained(
                        seed_artifact.directory,
                        local_files_only=True,
                        use_safetensors=True,
                    )
                )
                model.to(resolved_device)
                model.eval()

                seed_runtimes.append(
                    KcElectraSeedRuntime(
                        seed=seed_artifact.seed,
                        model=model,
                    ),
                )
        except Exception as error:
            raise KcElectraInferenceError(
                "KcELECTRA 모델 아티팩트를 "
                "로딩하지 못했습니다.",
            ) from error

        core_categories = tuple(
            contracts.cist.core_categories,
        )

        question_text_map = {
            question.question_code: (
                question.canonical_question
            )
            for question in contracts.cist.questions
        }
        question_category_map = {
            question.question_code: (
                question.question_type
            )
            for question in contracts.cist.questions
        }
        kcelectra_question_codes = {
            question.question_code
            for question in contracts.cist.questions
            if question.feature_usage.kcelectra
        }

        return cls(
            tokenizer=tokenizer,
            seed_runtimes=tuple(seed_runtimes),
            question_text_map=question_text_map,
            question_category_map=(
                question_category_map
            ),
            kcelectra_question_codes=(
                kcelectra_question_codes
            ),
            core_categories=core_categories,
            max_length=(
                artifacts.kcelectra_max_length
            ),
            model_version=(
                artifacts.kcelectra_directory.name
            ),
            device=resolved_device,
        )

    def infer(
        self,
        clips: tuple[
            KcElectraClipInput,
            ...,
        ],
    ) -> KcElectraInferenceResult:
        if not clips:
            raise ValueError(
                "KcELECTRA 추론용 문항이 없습니다.",
            )

        observed_codes: set[str] = set()
        model_inputs: list[str] = []
        categories: list[str] = []

        for clip in clips:
            question_code = clip.question_code

            if question_code in observed_codes:
                raise ValueError(
                    "KcELECTRA 입력 문항이 "
                    f"중복되었습니다: {question_code}",
                )

            observed_codes.add(question_code)

            if (
                question_code
                not in self._question_text_map
            ):
                raise ValueError(
                    "CIST 계약에 없는 문항입니다: "
                    f"{question_code}",
                )

            if (
                question_code
                not in self
                ._kcelectra_question_codes
            ):
                raise ValueError(
                    "KcELECTRA 사용 대상이 아닌 "
                    f"문항입니다: {question_code}",
                )

            if not isinstance(
                clip.raw_transcript,
                str,
            ):
                raise TypeError(
                    "raw_transcript는 문자열이어야 합니다.",
                )

            model_inputs.append(
                build_kcelectra_input(
                    question_text=(
                        self._question_text_map[
                            question_code
                        ]
                    ),
                    raw_transcript=(
                        clip.raw_transcript
                    ),
                ),
            )
            categories.append(
                self._question_category_map[
                    question_code
                ],
            )

        ensemble_logits = (
            self._predict_clip_logits(
                model_inputs,
            )
        )

        clip_results = tuple(
            KcElectraClipResult(
                question_code=clip.question_code,
                category=category,
                dementia_logit=float(logit),
            )
            for clip, category, logit in zip(
                clips,
                categories,
                ensemble_logits,
                strict=True,
            )
        )

        category_results = (
            self._pool_categories(
                clip_results,
            )
        )
        final_logit = float(
            np.mean(
                [
                    result.dementia_logit
                    for result in category_results
                ],
                dtype=np.float64,
            ),
        )

        if not isfinite(final_logit):
            raise KcElectraInferenceError(
                "KcELECTRA 최종 logit이 "
                "유효하지 않습니다.",
            )

        return KcElectraInferenceResult(
            model_version=self._model_version,
            seed_count=len(
                self._seed_runtimes,
            ),
            dementia_logit=final_logit,
            dementia_probability=(
                _sigmoid(final_logit)
            ),
            category_results=category_results,
            clip_results=clip_results,
        )

    def _predict_clip_logits(
        self,
        model_input_texts: list[str],
    ) -> np.ndarray:
        try:
            encoded = self._tokenizer(
                model_input_texts,
                add_special_tokens=True,
                truncation=True,
                max_length=self._max_length,
                padding=True,
                return_tensors="pt",
            )
            model_inputs = {
                key: value.to(self._device)
                for key, value in encoded.items()
            }
        except Exception as error:
            raise KcElectraInferenceError(
                "KcELECTRA 입력을 토큰화하지 "
                "못했습니다.",
            ) from error

        seed_logits: list[torch.Tensor] = []

        try:
            with torch.inference_mode():
                for runtime in self._seed_runtimes:
                    output = runtime.model(
                        **model_inputs,
                    )
                    logits = output.logits

                    if (
                        logits.ndim != 2
                        or logits.shape[0]
                        != len(model_input_texts)
                        or logits.shape[1] != 2
                    ):
                        raise KcElectraInferenceError(
                            "KcELECTRA 모델 출력 크기가 "
                            "올바르지 않습니다.",
                        )

                    dementia_logits = (
                        logits[:, 1]
                        - logits[:, 0]
                    )
                    seed_logits.append(
                        dementia_logits,
                    )

                ensemble_logits = (
                    torch.stack(
                        seed_logits,
                        dim=0,
                    )
                    .mean(dim=0)
                    .detach()
                    .cpu()
                    .to(torch.float64)
                    .numpy()
                )
        except KcElectraInferenceError:
            raise
        except Exception as error:
            raise KcElectraInferenceError(
                "KcELECTRA 모델 추론에 "
                "실패했습니다.",
            ) from error

        if not np.isfinite(
            ensemble_logits,
        ).all():
            raise KcElectraInferenceError(
                "KcELECTRA clip logit에 "
                "유효하지 않은 값이 있습니다.",
            )

        return ensemble_logits

    def _pool_categories(
        self,
        clip_results: tuple[
            KcElectraClipResult,
            ...,
        ],
    ) -> tuple[
        KcElectraCategoryResult,
        ...,
    ]:
        by_category: dict[
            str,
            list[KcElectraClipResult],
        ] = {
            category: []
            for category in self._core_categories
        }

        for result in clip_results:
            by_category[
                result.category
            ].append(result)

        missing_categories = [
            category
            for category, results
            in by_category.items()
            if not results
        ]

        if missing_categories:
            raise ValueError(
                "KcELECTRA 핵심 범주가 "
                f"누락되었습니다: {missing_categories}",
            )

        category_results: list[
            KcElectraCategoryResult
        ] = []

        for category in self._core_categories:
            results = by_category[category]
            category_logit = float(
                np.mean(
                    [
                        result.dementia_logit
                        for result in results
                    ],
                    dtype=np.float64,
                ),
            )

            category_results.append(
                KcElectraCategoryResult(
                    category=category,
                    dementia_logit=(
                        category_logit
                    ),
                    clip_count=len(results),
                ),
            )

        return tuple(category_results)


def build_kcelectra_input(
    *,
    question_text: str,
    raw_transcript: str,
) -> str:
    if not isinstance(question_text, str):
        raise TypeError(
            "question_text는 문자열이어야 합니다.",
        )

    if not isinstance(raw_transcript, str):
        raise TypeError(
            "raw_transcript는 문자열이어야 합니다.",
        )

    normalized_question = question_text.strip()

    if not normalized_question:
        raise ValueError(
            "질문 원문은 비어 있을 수 없습니다.",
        )

    # STT 문장 자체는 교정하거나 정규화하지 않는다.
    # 학습 데이터 생성 당시와 동일하게 앞뒤 공백만 제거한다.
    preserved_transcript = (
        raw_transcript.strip()
    )

    return (
        f"[질문] {normalized_question}\n"
        f"[답변] {preserved_transcript}"
    )


def _resolve_device(
    device: str | torch.device | None,
) -> torch.device:
    if device is None or str(device) == "auto":
        return torch.device(
            "cuda"
            if torch.cuda.is_available()
            else "cpu",
        )

    resolved = torch.device(device)

    if (
        resolved.type == "cuda"
        and not torch.cuda.is_available()
    ):
        raise KcElectraInferenceError(
            "CUDA를 사용할 수 없습니다.",
        )

    if resolved.type not in {
        "cpu",
        "cuda",
    }:
        raise KcElectraInferenceError(
            "KcELECTRA 추론 장치는 cpu 또는 "
            "cuda만 지원합니다.",
        )

    return resolved


def _sigmoid(
    logit: float,
) -> float:
    clipped = max(
        -50.0,
        min(50.0, logit),
    )

    return 1.0 / (
        1.0 + exp(-clipped)
    )