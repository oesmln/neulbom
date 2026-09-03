from math import exp, sqrt
from types import SimpleNamespace

import numpy as np
import pytest
import torch

from app.audio.preprocessing import (
    ProcessedAudio,
)
from app.inference.ast import (
    AstClipInput,
    AstInferenceService,
    AstSeedRuntime,
)


class FakeFeatureExtractor:
    def __call__(
        self,
        waveforms,
        *,
        sampling_rate: int,
        return_tensors: str,
    ) -> dict[str, torch.Tensor]:
        assert sampling_rate == 16_000
        assert return_tensors == "pt"

        # 각 세그먼트 첫 sample을 테스트용 특징으로 사용한다.
        values = [
            float(waveform[0])
            for waveform in waveforms
        ]

        return {
            "input_values": torch.tensor(
                values,
                dtype=torch.float32,
            ).unsqueeze(1),
        }


class FakeAstModel:
    def __init__(
        self,
        seed_bias: float,
    ) -> None:
        self._seed_bias = seed_bias

    def __call__(
        self,
        *,
        input_values: torch.Tensor,
    ) -> SimpleNamespace:
        normal_logits = torch.zeros(
            input_values.shape[0],
            dtype=torch.float32,
            device=input_values.device,
        )
        dementia_logits = (
            input_values[:, 0]
            + self._seed_bias
        )

        return SimpleNamespace(
            logits=torch.stack(
                [
                    normal_logits,
                    dementia_logits,
                ],
                dim=1,
            ),
        )


@pytest.fixture
def service() -> AstInferenceService:
    return AstInferenceService(
        feature_extractor=FakeFeatureExtractor(),
        seed_runtimes=(
            AstSeedRuntime(
                seed=42,
                model=FakeAstModel(0.0),
            ),
            AstSeedRuntime(
                seed=52,
                model=FakeAstModel(0.2),
            ),
            AstSeedRuntime(
                seed=62,
                model=FakeAstModel(0.4),
            ),
        ),
        question_category_map={
            "orientation_year": "orientation",
            "orientation_month": "orientation",
            "memory_registration_first": "memory",
            "attention_digit_span_4": "attention",
            "language_semantic_fluency": "language",
        },
        ast_question_codes={
            "orientation_year",
            "orientation_month",
            "memory_registration_first",
            "attention_digit_span_4",
            "language_semantic_fluency",
        },
        core_categories=(
            "orientation",
            "memory",
            "attention",
            "language",
        ),
        sampling_rate=16_000,
        model_version="test-ast-v1",
        device=torch.device("cpu"),
    )


def test_ensembles_segments_and_pools_hierarchy(
    service: AstInferenceService,
) -> None:
    result = service.infer(
        (
            AstClipInput(
                question_code="orientation_year",
                audio=_audio(0.1, 0.5),
            ),
            AstClipInput(
                question_code="orientation_month",
                audio=_audio(0.3),
            ),
            AstClipInput(
                question_code=(
                    "memory_registration_first"
                ),
                audio=_audio(0.2),
            ),
            AstClipInput(
                question_code=(
                    "attention_digit_span_4"
                ),
                audio=_audio(0.4),
            ),
            AstClipInput(
                question_code=(
                    "language_semantic_fluency"
                ),
                audio=_audio(0.6),
            ),
        ),
    )

    # seed bias 평균은 0.2다.
    orientation_first = (
        result.clip_results[0]
    )
    assert orientation_first.segment_logits == (
        pytest.approx(
            (0.3, 0.7),
        )
    )
    assert (
        orientation_first.dementia_logit
        == pytest.approx(0.5)
    )
    assert orientation_first.segment_count == 2

    categories = {
        category.category: category
        for category in result.category_results
    }

    assert (
        categories["orientation"]
        .dementia_logit
        == pytest.approx(0.5)
    )
    assert (
        categories["orientation"]
        .clip_count
        == 2
    )
    assert (
        categories["memory"]
        .dementia_logit
        == pytest.approx(0.4)
    )
    assert (
        categories["attention"]
        .dementia_logit
        == pytest.approx(0.6)
    )
    assert (
        categories["language"]
        .dementia_logit
        == pytest.approx(0.8)
    )

    expected_logit = (
        0.5 * sqrt(2)
        + 0.4
        + 0.6
        + 0.8
    ) / (
        sqrt(2)
        + 3
    )
    expected_probability = (
        1.0
        / (
            1.0
            + exp(-expected_logit)
        )
    )

    assert result.dementia_logit == (
        pytest.approx(expected_logit)
    )
    assert result.dementia_probability == (
        pytest.approx(expected_probability)
    )
    assert result.seed_count == 3
    assert result.model_version == "test-ast-v1"


def test_discards_remainder_shorter_than_one_second(
    service: AstInferenceService,
) -> None:
    waveform = np.full(
        4 * 16_000 + 8_000,
        0.25,
        dtype=np.float32,
    )

    result = service.infer(
        (
            AstClipInput(
                question_code="orientation_year",
                audio=_processed_audio(waveform),
            ),
            AstClipInput(
                question_code=(
                    "memory_registration_first"
                ),
                audio=_audio(0.2),
            ),
            AstClipInput(
                question_code=(
                    "attention_digit_span_4"
                ),
                audio=_audio(0.4),
            ),
            AstClipInput(
                question_code=(
                    "language_semantic_fluency"
                ),
                audio=_audio(0.6),
            ),
        ),
    )

    assert (
        result.clip_results[0].segment_count
        == 1
    )


def test_rejects_missing_core_category(
    service: AstInferenceService,
) -> None:
    with pytest.raises(
        ValueError,
        match="핵심 범주가 누락",
    ):
        service.infer(
            (
                AstClipInput(
                    question_code="orientation_year",
                    audio=_audio(0.1),
                ),
                AstClipInput(
                    question_code=(
                        "memory_registration_first"
                    ),
                    audio=_audio(0.2),
                ),
                AstClipInput(
                    question_code=(
                        "attention_digit_span_4"
                    ),
                    audio=_audio(0.4),
                ),
            ),
        )


def test_rejects_duplicate_question_code(
    service: AstInferenceService,
) -> None:
    with pytest.raises(
        ValueError,
        match="중복",
    ):
        service.infer(
            (
                AstClipInput(
                    question_code="orientation_year",
                    audio=_audio(0.1),
                ),
                AstClipInput(
                    question_code="orientation_year",
                    audio=_audio(0.2),
                ),
            ),
        )


def test_rejects_wrong_sample_rate(
    service: AstInferenceService,
) -> None:
    audio = ProcessedAudio(
        waveform=np.zeros(
            16_000,
            dtype=np.float32,
        ),
        sample_rate=8_000,
        duration_ms=2_000,
        source_content_type="audio/wav",
    )

    with pytest.raises(
        ValueError,
        match="sampling rate",
    ):
        service.infer(
            (
                AstClipInput(
                    question_code="orientation_year",
                    audio=audio,
                ),
            ),
        )


def _audio(
    *segment_values: float,
) -> ProcessedAudio:
    waveform = np.concatenate(
        [
            np.full(
                4 * 16_000,
                value,
                dtype=np.float32,
            )
            for value in segment_values
        ],
    )

    return _processed_audio(waveform)


def _processed_audio(
    waveform: np.ndarray,
) -> ProcessedAudio:
    return ProcessedAudio(
        waveform=waveform,
        sample_rate=16_000,
        duration_ms=round(
            waveform.size
            / 16_000
            * 1_000,
        ),
        source_content_type="audio/wav",
    )