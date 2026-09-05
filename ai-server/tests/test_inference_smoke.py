from pathlib import Path

import numpy as np

from app.contracts.loader import (
    load_contract_bundle,
)
from app.core.config import PROJECT_ROOT
from app.inference.smoke import (
    _create_synthetic_audio,
    _select_smoke_question_codes,
)


def test_selects_one_question_per_core_category() -> None:
    contracts = load_contract_bundle(
        PROJECT_ROOT / "contracts",
    )
    questions_by_code = {
        question.question_code: question
        for question
        in contracts.cist.questions
    }

    for feature_name in (
        "ast",
        "kcelectra",
    ):
        question_codes = (
            _select_smoke_question_codes(
                contracts=contracts,
                feature_name=feature_name,
            )
        )

        selected_questions = tuple(
            questions_by_code[question_code]
            for question_code
            in question_codes
        )

        assert len(question_codes) == len(
            contracts.cist.core_categories,
        )
        assert len(question_codes) == len(
            set(question_codes),
        )
        assert tuple(
            question.question_type
            for question
            in selected_questions
        ) == tuple(
            contracts.cist.core_categories,
        )
        assert all(
            getattr(
                question.feature_usage,
                feature_name,
            )
            for question
            in selected_questions
        )


def test_creates_deterministic_synthetic_audio() -> None:
    first = _create_synthetic_audio(
        sample_rate=16_000,
    )
    second = _create_synthetic_audio(
        sample_rate=16_000,
    )

    assert first.sample_rate == 16_000
    assert first.duration_ms == 1000
    assert first.source_content_type == (
        "audio/wav"
    )
    assert first.waveform.shape == (
        16_000,
    )
    assert first.waveform.dtype == (
        np.float32
    )
    assert np.isfinite(
        first.waveform,
    ).all()
    assert np.array_equal(
        first.waveform,
        second.waveform,
    )