from dataclasses import dataclass
from math import exp, isfinite, sqrt
from typing import Any

import numpy as np
import torch
from transformers import (
    AutoFeatureExtractor,
    AutoModelForAudioClassification,
)

from app.audio.preprocessing import (
    ProcessedAudio,
    create_ast_segments,
)
from app.contracts.models import ContractBundle
from app.inference.artifacts import (
    EXPECTED_SEEDS,
    ModelArtifactBundle,
)


class AstInferenceError(RuntimeError):
    """AST 모델 로딩 또는 추론에 실패한 경우 발생한다."""


@dataclass(frozen=True, slots=True)
class AstClipInput:
    question_code: str
    audio: ProcessedAudio


@dataclass(frozen=True, slots=True)
class AstSeedRuntime:
    seed: int
    model: Any


@dataclass(frozen=True, slots=True)
class AstClipResult:
    question_code: str
    category: str
    segment_logits: tuple[float, ...]
    dementia_logit: float
    segment_count: int


@dataclass(frozen=True, slots=True)
class AstCategoryResult:
    category: str
    dementia_logit: float
    clip_count: int
    segment_count: int


@dataclass(frozen=True, slots=True)
class AstInferenceResult:
    model_version: str
    seed_count: int
    dementia_logit: float
    dementia_probability: float
    category_results: tuple[
        AstCategoryResult,
        ...,
    ]
    clip_results: tuple[
        AstClipResult,
        ...,
    ]


class AstInferenceService:
    def __init__(
        self,
        *,
        feature_extractor: Any,
        seed_runtimes: tuple[
            AstSeedRuntime,
            ...,
        ],
        question_category_map: dict[str, str],
        ast_question_codes: set[str],
        core_categories: tuple[str, ...],
        sampling_rate: int,
        model_version: str,
        device: torch.device,
    ) -> None:
        actual_seeds = tuple(
            runtime.seed
            for runtime in seed_runtimes
        )

        if actual_seeds != EXPECTED_SEEDS:
            raise ValueError(
                "AST seed 순서가 계약과 "
                "일치하지 않습니다.",
            )

        if sampling_rate <= 0:
            raise ValueError(
                "AST sampling rate는 "
                "1 이상이어야 합니다.",
            )

        if not core_categories:
            raise ValueError(
                "AST 핵심 범주가 필요합니다.",
            )

        if set(question_category_map.values()) != set(
            core_categories,
        ):
            raise ValueError(
                "AST 문항 범주가 핵심 범주와 "
                "일치하지 않습니다.",
            )

        if not ast_question_codes:
            raise ValueError(
                "AST 사용 문항이 필요합니다.",
            )

        self._feature_extractor = feature_extractor
        self._seed_runtimes = seed_runtimes
        self._question_category_map = dict(
            question_category_map,
        )
        self._ast_question_codes = set(
            ast_question_codes,
        )
        self._core_categories = core_categories
        self._sampling_rate = sampling_rate
        self._model_version = model_version
        self._device = device

    @classmethod
    def from_artifacts(
        cls,
        *,
        artifacts: ModelArtifactBundle,
        contracts: ContractBundle,
        device: str | torch.device | None = None,
    ) -> "AstInferenceService":
        resolved_device = _resolve_device(
            device,
        )

        first_seed = artifacts.ast_seeds[0]

        try:
            feature_extractor = (
                AutoFeatureExtractor.from_pretrained(
                    first_seed.directory,
                    local_files_only=True,
                )
            )

            seed_runtimes: list[
                AstSeedRuntime
            ] = []

            for seed_artifact in artifacts.ast_seeds:
                model = (
                    AutoModelForAudioClassification
                    .from_pretrained(
                        seed_artifact.directory,
                        local_files_only=True,
                        use_safetensors=True,
                    )
                )
                model.to(resolved_device)
                model.eval()

                seed_runtimes.append(
                    AstSeedRuntime(
                        seed=seed_artifact.seed,
                        model=model,
                    ),
                )
        except Exception as error:
            raise AstInferenceError(
                "AST 모델 아티팩트를 "
                "로딩하지 못했습니다.",
            ) from error

        core_categories = tuple(
            contracts.cist.core_categories,
        )

        question_category_map = {
            question.question_code: (
                question.question_type
            )
            for question in contracts.cist.questions
            if (
                question.question_type
                in core_categories
            )
        }

        ast_question_codes = {
            question.question_code
            for question in contracts.cist.questions
            if question.feature_usage.ast
        }

        return cls(
            feature_extractor=feature_extractor,
            seed_runtimes=tuple(seed_runtimes),
            question_category_map=(
                question_category_map
            ),
            ast_question_codes=(
                ast_question_codes
            ),
            core_categories=core_categories,
            sampling_rate=(
                artifacts.ast_sampling_rate
            ),
            model_version=(
                artifacts.ast_directory.name
            ),
            device=resolved_device,
        )

    def infer(
        self,
        clips: tuple[AstClipInput, ...],
    ) -> AstInferenceResult:
        if not clips:
            raise ValueError(
                "AST 추론용 음성 클립이 없습니다.",
            )

        observed_question_codes: set[str] = set()
        clip_results: list[AstClipResult] = []

        for clip in clips:
            question_code = clip.question_code

            if (
                question_code
                in observed_question_codes
            ):
                raise ValueError(
                    "AST 입력 문항이 중복되었습니다: "
                    f"{question_code}",
                )

            observed_question_codes.add(
                question_code,
            )

            if (
                question_code
                not in self._question_category_map
            ):
                raise ValueError(
                    "CIST 계약에 없는 문항입니다: "
                    f"{question_code}",
                )

            if (
                question_code
                not in self._ast_question_codes
            ):
                raise ValueError(
                    "AST 사용 대상이 아닌 문항입니다: "
                    f"{question_code}",
                )

            if (
                clip.audio.sample_rate
                != self._sampling_rate
            ):
                raise ValueError(
                    "AST 입력 음성의 sampling rate가 "
                    "모델 설정과 일치하지 않습니다.",
                )

            category = self._question_category_map[
                question_code
            ]
            segment_logits = (
                self._infer_clip_segments(
                    clip.audio,
                )
            )
            clip_logit = float(
                np.mean(
                    np.asarray(
                        segment_logits,
                        dtype=np.float64,
                    ),
                ),
            )

            if not isfinite(clip_logit):
                raise AstInferenceError(
                    "AST 클립 logit이 "
                    "유효하지 않습니다.",
                )

            clip_results.append(
                AstClipResult(
                    question_code=question_code,
                    category=category,
                    segment_logits=segment_logits,
                    dementia_logit=clip_logit,
                    segment_count=len(
                        segment_logits,
                    ),
                ),
            )

        category_results = (
            self._pool_categories(
                clip_results,
            )
        )
        final_logit = (
            self._pool_person_logit(
                category_results,
            )
        )
        probability = _sigmoid(
            final_logit,
        )

        return AstInferenceResult(
            model_version=self._model_version,
            seed_count=len(
                self._seed_runtimes,
            ),
            dementia_logit=final_logit,
            dementia_probability=probability,
            category_results=category_results,
            clip_results=tuple(clip_results),
        )

    def _infer_clip_segments(
        self,
        audio: ProcessedAudio,
    ) -> tuple[float, ...]:
        segments = create_ast_segments(
            audio,
        )
        waveforms = [
            segment.waveform
            for segment in segments
        ]

        try:
            features = self._feature_extractor(
                waveforms,
                sampling_rate=self._sampling_rate,
                return_tensors="pt",
            )
            input_values = features[
                "input_values"
            ].to(self._device)
        except Exception as error:
            raise AstInferenceError(
                "AST 입력 특징을 생성하지 못했습니다.",
            ) from error

        seed_logits: list[torch.Tensor] = []

        try:
            with torch.inference_mode():
                for runtime in self._seed_runtimes:
                    output = runtime.model(
                        input_values=input_values,
                    )
                    logits = output.logits

                    if (
                        logits.ndim != 2
                        or logits.shape[0]
                        != len(segments)
                        or logits.shape[1] != 2
                    ):
                        raise AstInferenceError(
                            "AST 모델 출력 크기가 "
                            "올바르지 않습니다.",
                        )

                    # 학습과 동일하게 치매 logit에서
                    # 정상 logit을 빼 이진 log-odds를 만든다.
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
        except AstInferenceError:
            raise
        except Exception as error:
            raise AstInferenceError(
                "AST 모델 추론에 실패했습니다.",
            ) from error

        if not np.isfinite(
            ensemble_logits,
        ).all():
            raise AstInferenceError(
                "AST segment logit에 "
                "유효하지 않은 값이 있습니다.",
            )

        return tuple(
            float(value)
            for value in ensemble_logits
        )

    def _pool_categories(
        self,
        clip_results: list[
            AstClipResult
        ],
    ) -> tuple[AstCategoryResult, ...]:
        by_category: dict[
            str,
            list[AstClipResult],
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
                "AST 핵심 범주가 누락되었습니다: "
                f"{missing_categories}",
            )

        category_results: list[
            AstCategoryResult
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
                AstCategoryResult(
                    category=category,
                    dementia_logit=(
                        category_logit
                    ),
                    clip_count=len(results),
                    segment_count=sum(
                        result.segment_count
                        for result in results
                    ),
                ),
            )

        return tuple(category_results)

    def _pool_person_logit(
        self,
        category_results: tuple[
            AstCategoryResult,
            ...,
        ],
    ) -> float:
        weights = np.asarray(
            [
                sqrt(result.clip_count)
                for result in category_results
            ],
            dtype=np.float64,
        )
        logits = np.asarray(
            [
                result.dementia_logit
                for result in category_results
            ],
            dtype=np.float64,
        )

        final_logit = float(
            np.average(
                logits,
                weights=weights,
            ),
        )

        if not isfinite(final_logit):
            raise AstInferenceError(
                "AST 최종 logit이 "
                "유효하지 않습니다.",
            )

        return final_logit


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
        raise AstInferenceError(
            "CUDA를 사용할 수 없습니다.",
        )

    if resolved.type not in {
        "cpu",
        "cuda",
    }:
        raise AstInferenceError(
            "AST 추론 장치는 cpu 또는 "
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