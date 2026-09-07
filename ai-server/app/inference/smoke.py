import argparse
import gc
import json
from pathlib import Path
from time import perf_counter
from typing import Any, Literal

import numpy as np
import torch

from app.audio.preprocessing import (
    ProcessedAudio,
)
from app.contracts.loader import (
    load_contract_bundle,
)
from app.contracts.models import ContractBundle
from app.core.config import get_settings
from app.inference.artifacts import (
    discover_model_artifacts,
)
from app.inference.ast import (
    AstClipInput,
    AstInferenceService,
)
from app.inference.fusion import (
    FusionFeatures,
    FusionInferenceService,
)
from app.inference.kcelectra import (
    KcElectraClipInput,
    KcElectraInferenceService,
)

FeatureName = Literal[
    "ast",
    "kcelectra",
]

_SMOKE_TRANSCRIPTS = {
    "orientation": "2026년 9월 금요일 집",
    "memory": "민수 자전거 공원 11시 야구",
    "attention": "삼 일 구",
    "language": (
        "호랑이 사자 토끼 강아지 고양이"
    ),
}


def main() -> int:
    args = _parse_args()

    try:
        report = run_inference_smoke(
            artifacts_dir=args.artifacts_dir,
            contracts_dir=args.contracts_dir,
            device=args.device,
        )
    except Exception as error:
        report = {
            "schema_version": (
                "inference-smoke-v1"
            ),
            "status": "failed",
            "errors": _describe_error_chain(
                error,
            ),
        }

        print(
            json.dumps(
                report,
                ensure_ascii=False,
                indent=2,
            ),
            flush=True,
        )
        return 1

    print(
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
        ),
        flush=True,
    )
    return 0


def run_inference_smoke(
    *,
    artifacts_dir: Path,
    contracts_dir: Path,
    device: str,
) -> dict[str, Any]:
    total_started = perf_counter()
    resolved_device = _resolve_device(
        device,
    )

    contract_started = perf_counter()
    contracts = load_contract_bundle(
        contracts_dir.resolve(),
    )
    contract_seconds = _elapsed_seconds(
        contract_started,
    )

    artifact_started = perf_counter()
    artifacts = discover_model_artifacts(
        artifacts_dir.resolve(),
    )
    artifact_seconds = _elapsed_seconds(
        artifact_started,
    )

    ast_question_codes = (
        _select_smoke_question_codes(
            contracts=contracts,
            feature_name="ast",
        )
    )
    kcelectra_question_codes = (
        _select_smoke_question_codes(
            contracts=contracts,
            feature_name="kcelectra",
        )
    )

    synthetic_audio = (
        _create_synthetic_audio(
            sample_rate=(
                artifacts.ast_sampling_rate
            ),
        )
    )

    ast_load_started = perf_counter()
    ast_service = (
        AstInferenceService.from_artifacts(
            artifacts=artifacts,
            contracts=contracts,
            device=resolved_device,
        )
    )
    ast_load_seconds = _elapsed_seconds(
        ast_load_started,
    )

    ast_inference_started = perf_counter()
    ast_result = ast_service.infer(
        tuple(
            AstClipInput(
                question_code=question_code,
                audio=synthetic_audio,
            )
            for question_code
            in ast_question_codes
        ),
    )
    ast_inference_seconds = (
        _elapsed_seconds(
            ast_inference_started,
        )
    )

    if ast_result.seed_count != 3:
        raise RuntimeError(
            "AST seed 모델 3개가 "
            "사용되지 않았습니다.",
        )

    ast_report = {
        "model_version": (
            ast_result.model_version
        ),
        "seed_count": ast_result.seed_count,
        "question_codes": list(
            ast_question_codes,
        ),
        "dementia_logit": (
            ast_result.dementia_logit
        ),
        "dementia_probability": (
            ast_result.dementia_probability
        ),
        "load_seconds": ast_load_seconds,
        "inference_seconds": (
            ast_inference_seconds
        ),
    }

    del ast_service
    gc.collect()
    _clear_cuda_cache(
        resolved_device,
    )

    kcelectra_load_started = (
        perf_counter()
    )
    kcelectra_service = (
        KcElectraInferenceService
        .from_artifacts(
            artifacts=artifacts,
            contracts=contracts,
            device=resolved_device,
        )
    )
    kcelectra_load_seconds = (
        _elapsed_seconds(
            kcelectra_load_started,
        )
    )

    question_category_by_code = {
        question.question_code: (
            question.question_type
        )
        for question
        in contracts.cist.questions
    }

    kcelectra_inference_started = (
        perf_counter()
    )
    kcelectra_result = (
        kcelectra_service.infer(
            tuple(
                KcElectraClipInput(
                    question_code=(
                        question_code
                    ),
                    raw_transcript=(
                        _SMOKE_TRANSCRIPTS.get(
                            question_category_by_code[
                                question_code
                            ],
                            "테스트 응답",
                        )
                    ),
                )
                for question_code
                in kcelectra_question_codes
            ),
        )
    )
    kcelectra_inference_seconds = (
        _elapsed_seconds(
            kcelectra_inference_started,
        )
    )

    if kcelectra_result.seed_count != 3:
        raise RuntimeError(
            "KcELECTRA seed 모델 3개가 "
            "사용되지 않았습니다.",
        )

    kcelectra_report = {
        "model_version": (
            kcelectra_result.model_version
        ),
        "seed_count": (
            kcelectra_result.seed_count
        ),
        "question_codes": list(
            kcelectra_question_codes,
        ),
        "dementia_logit": (
            kcelectra_result.dementia_logit
        ),
        "dementia_probability": (
            kcelectra_result
            .dementia_probability
        ),
        "load_seconds": (
            kcelectra_load_seconds
        ),
        "inference_seconds": (
            kcelectra_inference_seconds
        ),
    }

    del kcelectra_service
    gc.collect()
    _clear_cuda_cache(
        resolved_device,
    )

    fusion_load_started = perf_counter()
    fusion_service = (
        FusionInferenceService
        .from_artifacts(
            artifacts,
        )
    )
    fusion_load_seconds = (
        _elapsed_seconds(
            fusion_load_started,
        )
    )

    fusion_features = FusionFeatures(
        ast_logit=ast_result.dementia_logit,
        kcelectra_logit=(
            kcelectra_result.dementia_logit
        ),
        category_balanced_wrong_event_score=(
            0.0
        ),
        category_balanced_median_delay=1.0,
    )

    fusion_inference_started = (
        perf_counter()
    )
    fusion_result = fusion_service.infer(
        fusion_features,
    )
    fusion_inference_seconds = (
        _elapsed_seconds(
            fusion_inference_started,
        )
    )

    fusion_report = {
        "model_version": (
            fusion_result.model_version
        ),
        "model_score": (
            fusion_result.model_score
        ),
        "decision_threshold": (
            fusion_result.decision_threshold
        ),
        "threshold_version": (
            fusion_result.threshold_version
        ),
        "review_threshold": (
            fusion_result.review_threshold
        ),
        "risk_flag": (
            fusion_result.risk_flag
        ),
        "risk_level": (
            fusion_result.risk_level.value
        ),
        "load_seconds": (
            fusion_load_seconds
        ),
        "inference_seconds": (
            fusion_inference_seconds
        ),
    }

    return {
        "schema_version": (
            "inference-smoke-v1"
        ),
        "status": "passed",
        "device": resolved_device,
        "torch_version": torch.__version__,
        "cuda_available": (
            torch.cuda.is_available()
        ),
        "artifacts_root": str(
            artifacts.root,
        ),
        "contracts_root": str(
            contracts_dir.resolve(),
        ),
        "preflight": {
            "contract_seconds": (
                contract_seconds
            ),
            "artifact_seconds": (
                artifact_seconds
            ),
            "transformers_version": (
                artifacts
                .transformers_version
            ),
        },
        "ast": ast_report,
        "kcelectra": kcelectra_report,
        "fusion": fusion_report,
        "total_seconds": (
            _elapsed_seconds(
                total_started,
            )
        ),
    }


def _select_smoke_question_codes(
    *,
    contracts: ContractBundle,
    feature_name: FeatureName,
) -> tuple[str, ...]:
    selected_codes: list[str] = []

    for category in (
        contracts.cist.core_categories
    ):
        matching_question = next(
            (
                question
                for question
                in sorted(
                    contracts.cist.questions,
                    key=lambda item: item.order,
                )
                if (
                    question.question_type
                    == category
                    and getattr(
                        question.feature_usage,
                        feature_name,
                    )
                )
            ),
            None,
        )

        if matching_question is None:
            raise RuntimeError(
                "스모크 추론용 문항을 "
                "선택할 수 없습니다: "
                f"feature={feature_name}, "
                f"category={category}",
            )

        selected_codes.append(
            matching_question.question_code,
        )

    return tuple(selected_codes)


def _create_synthetic_audio(
    *,
    sample_rate: int,
    duration_seconds: float = 1.0,
) -> ProcessedAudio:
    if sample_rate <= 0:
        raise ValueError(
            "sample_rate는 1 이상이어야 합니다.",
        )

    if duration_seconds <= 0:
        raise ValueError(
            "duration_seconds는 "
            "0보다 커야 합니다.",
        )

    sample_count = round(
        sample_rate * duration_seconds,
    )
    timeline = (
        np.arange(
            sample_count,
            dtype=np.float32,
        )
        / float(sample_rate)
    )
    waveform = (
        0.01
        * np.sin(
            2.0
            * np.pi
            * 220.0
            * timeline
        )
    ).astype(
        np.float32,
        copy=False,
    )

    return ProcessedAudio(
        waveform=np.ascontiguousarray(
            waveform,
        ),
        sample_rate=sample_rate,
        duration_ms=round(
            duration_seconds * 1000,
        ),
        source_content_type="audio/wav",
    )


def _resolve_device(
    device: str,
) -> str:
    if device == "auto":
        return (
            "cuda"
            if torch.cuda.is_available()
            else "cpu"
        )

    if device == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError(
                "CUDA를 사용할 수 없습니다.",
            )

        return "cuda"

    if device == "cpu":
        return "cpu"

    raise ValueError(
        "device는 auto, cpu, cuda 중 "
        "하나여야 합니다.",
    )


def _clear_cuda_cache(
    device: str,
) -> None:
    if device == "cuda":
        torch.cuda.empty_cache()


def _elapsed_seconds(
    started: float,
) -> float:
    return round(
        perf_counter() - started,
        3,
    )


def _describe_error_chain(
    error: BaseException,
) -> list[dict[str, str]]:
    errors: list[dict[str, str]] = []
    current: BaseException | None = error
    observed_ids: set[int] = set()

    while (
        current is not None
        and id(current) not in observed_ids
    ):
        observed_ids.add(id(current))
        errors.append(
            {
                "type": (
                    type(current).__name__
                ),
                "message": str(current),
            },
        )
        current = (
            current.__cause__
            or current.__context__
        )

    return errors


def _parse_args() -> argparse.Namespace:
    settings = get_settings()

    parser = argparse.ArgumentParser(
        description=(
            "실제 AST, KcELECTRA, fusion "
            "아티팩트를 로드해 최소 추론을 "
            "실행합니다."
        ),
    )
    parser.add_argument(
        "--artifacts-dir",
        type=Path,
        default=settings.artifacts_dir,
    )
    parser.add_argument(
        "--contracts-dir",
        type=Path,
        default=settings.contracts_dir,
    )
    parser.add_argument(
        "--device",
        choices=(
            "auto",
            "cpu",
            "cuda",
        ),
        default="cpu",
    )

    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(main())