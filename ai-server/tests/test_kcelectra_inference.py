from math import exp
from types import SimpleNamespace

import pytest
import torch

from app.inference.kcelectra import (
    KcElectraClipInput,
    KcElectraInferenceService,
    KcElectraSeedRuntime,
    build_kcelectra_input,
)


class FakeTokenizer:
    def __init__(self) -> None:
        self.received_texts: list[str] = []
        self.received_options: dict = {}

    def __call__(
        self,
        texts,
        **options,
    ) -> dict[str, torch.Tensor]:
        self.received_texts = list(texts)
        self.received_options = dict(options)

        answer_values = {
            "방향답변1": 0.1,
            "방향답변2": 0.3,
            "기억답변": 0.2,
            "주의답변": 0.4,
            "언어답변": 0.6,
        }

        values = []

        for text in texts:
            answer = text.split(
                "\n[답변] ",
                maxsplit=1,
            )[1]
            values.append(
                answer_values[answer],
            )

        input_ids = torch.tensor(
            values,
            dtype=torch.float32,
        ).unsqueeze(1)

        return {
            "input_ids": input_ids,
            "attention_mask": torch.ones_like(
                input_ids,
            ),
        }


class FakeKcElectraModel:
    def __init__(
        self,
        seed_bias: float,
    ) -> None:
        self._seed_bias = seed_bias

    def __call__(
        self,
        *,
        input_ids: torch.Tensor,
        attention_mask: torch.Tensor,
    ) -> SimpleNamespace:
        assert attention_mask.shape == (
            input_ids.shape
        )

        normal_logits = torch.zeros(
            input_ids.shape[0],
            dtype=torch.float32,
            device=input_ids.device,
        )
        dementia_logits = (
            input_ids[:, 0]
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
def tokenizer() -> FakeTokenizer:
    return FakeTokenizer()


@pytest.fixture
def service(
    tokenizer: FakeTokenizer,
) -> KcElectraInferenceService:
    return KcElectraInferenceService(
        tokenizer=tokenizer,
        seed_runtimes=(
            KcElectraSeedRuntime(
                seed=42,
                model=FakeKcElectraModel(0.0),
            ),
            KcElectraSeedRuntime(
                seed=52,
                model=FakeKcElectraModel(0.2),
            ),
            KcElectraSeedRuntime(
                seed=62,
                model=FakeKcElectraModel(0.4),
            ),
        ),
        question_text_map={
            "orientation_year": "올해는 몇 년도입니까?",
            "orientation_month": "지금은 몇 월입니까?",
            "memory_registration_first": (
                "문장을 따라 말씀해 주세요."
            ),
            "attention_digit_span_4": (
                "숫자를 따라 말씀해 주세요."
            ),
            "language_semantic_fluency": (
                "과일이나 채소를 말씀해 주세요."
            ),
        },
        question_category_map={
            "orientation_year": "orientation",
            "orientation_month": "orientation",
            "memory_registration_first": "memory",
            "attention_digit_span_4": "attention",
            "language_semantic_fluency": "language",
        },
        kcelectra_question_codes={
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
        max_length=256,
        model_version="test-kcelectra-v1",
        device=torch.device("cpu"),
    )


def test_builds_training_compatible_input() -> None:
    result = build_kcelectra_input(
        question_text=(
            " 올해는 몇 년도입니까? "
        ),
        raw_transcript=(
            "  이천이십육 년입니다!  "
        ),
    )

    assert result == (
        "[질문] 올해는 몇 년도입니까?\n"
        "[답변] 이천이십육 년입니다!"
    )


def test_preserves_empty_raw_transcript() -> None:
    result = build_kcelectra_input(
        question_text="여기는 어디인가요?",
        raw_transcript="   ",
    )

    assert result == (
        "[질문] 여기는 어디인가요?\n"
        "[답변] "
    )


def test_ensembles_and_pools_categories(
    service: KcElectraInferenceService,
    tokenizer: FakeTokenizer,
) -> None:
    result = service.infer(
        (
            KcElectraClipInput(
                question_code="orientation_year",
                raw_transcript="방향답변1",
            ),
            KcElectraClipInput(
                question_code="orientation_month",
                raw_transcript="방향답변2",
            ),
            KcElectraClipInput(
                question_code=(
                    "memory_registration_first"
                ),
                raw_transcript="기억답변",
            ),
            KcElectraClipInput(
                question_code=(
                    "attention_digit_span_4"
                ),
                raw_transcript="주의답변",
            ),
            KcElectraClipInput(
                question_code=(
                    "language_semantic_fluency"
                ),
                raw_transcript="언어답변",
            ),
        ),
    )

    # seed bias 평균은 0.2다.
    assert result.clip_results[0].dementia_logit == (
        pytest.approx(0.3)
    )
    assert result.clip_results[1].dementia_logit == (
        pytest.approx(0.5)
    )

    categories = {
        result.category: result
        for result in result.category_results
    }

    assert (
        categories["orientation"]
        .dementia_logit
        == pytest.approx(0.4)
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
        0.4
        + 0.4
        + 0.6
        + 0.8
    ) / 4
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
    assert result.model_version == (
        "test-kcelectra-v1"
    )

    assert tokenizer.received_texts[0] == (
        "[질문] 올해는 몇 년도입니까?\n"
        "[답변] 방향답변1"
    )
    assert tokenizer.received_options == {
        "add_special_tokens": True,
        "truncation": True,
        "max_length": 256,
        "padding": True,
        "return_tensors": "pt",
    }


def test_rejects_missing_core_category(
    service: KcElectraInferenceService,
) -> None:
    with pytest.raises(
        ValueError,
        match="핵심 범주가 누락",
    ):
        service.infer(
            (
                KcElectraClipInput(
                    question_code=(
                        "orientation_year"
                    ),
                    raw_transcript="방향답변1",
                ),
                KcElectraClipInput(
                    question_code=(
                        "memory_registration_first"
                    ),
                    raw_transcript="기억답변",
                ),
                KcElectraClipInput(
                    question_code=(
                        "attention_digit_span_4"
                    ),
                    raw_transcript="주의답변",
                ),
            ),
        )


def test_rejects_duplicate_question_code(
    service: KcElectraInferenceService,
) -> None:
    with pytest.raises(
        ValueError,
        match="중복",
    ):
        service.infer(
            (
                KcElectraClipInput(
                    question_code=(
                        "orientation_year"
                    ),
                    raw_transcript="방향답변1",
                ),
                KcElectraClipInput(
                    question_code=(
                        "orientation_year"
                    ),
                    raw_transcript="방향답변2",
                ),
            ),
        )


def test_rejects_unknown_question_code(
    service: KcElectraInferenceService,
) -> None:
    with pytest.raises(
        ValueError,
        match="계약에 없는",
    ):
        service.infer(
            (
                KcElectraClipInput(
                    question_code=(
                        "unknown_question"
                    ),
                    raw_transcript="방향답변1",
                ),
            ),
        )