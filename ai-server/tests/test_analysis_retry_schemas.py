from copy import deepcopy
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.api.schemas.analysis import (
    AnalysisRetryRequest,
    ReissueAudioUrlItem,
    ReplaceResponseItem,
)


def audio_payload() -> dict:
    return {
        "signed_url": (
            "https://storage.example/"
            "response.wav?signature=test"
        ),
        "expires_at": (
            "2099-01-01T00:00:00Z"
        ),
        "content_type": "audio/wav",
        "size_bytes": 1234,
    }


def reissue_item_payload() -> dict:
    return {
        "question_code": (
            "orientation_year"
        ),
        "retry_action": (
            "REISSUE_AUDIO_URL"
        ),
        "recording_id": str(uuid4()),
        "response_id": str(uuid4()),
        "audio": audio_payload(),
    }


def replacement_item_payload() -> dict:
    return {
        "question_code": (
            "orientation_year"
        ),
        "retry_action": (
            "REPLACE_RESPONSE"
        ),
        "variant_id": (
            "orientation-year-fixed-v1"
        ),
        "recording_id": str(uuid4()),
        "response_id": str(uuid4()),
        "audio": audio_payload(),
        "stt": {
            "status": "success",
            "raw_transcript": "2026년",
        },
        "timing": {
            "prompt_end_to_recording_start_ms": (
                100
            ),
            "recording_duration_ms": 1200,
        },
    }


def test_accepts_reissued_audio_url() -> None:
    request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "items": [
                    reissue_item_payload(),
                ],
            },
        )
    )

    assert len(request.items) == 1
    assert isinstance(
        request.items[0],
        ReissueAudioUrlItem,
    )
    assert request.items[0].retry_action == (
        "REISSUE_AUDIO_URL"
    )


def test_accepts_replacement_response() -> None:
    request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "items": [
                    replacement_item_payload(),
                ],
            },
        )
    )

    assert isinstance(
        request.items[0],
        ReplaceResponseItem,
    )
    assert request.items[0].retry_action == (
        "REPLACE_RESPONSE"
    )


def test_audio_error_allows_full_replacement() -> None:
    request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "AUDIO_DOWNLOAD_FAILED"
                ),
                "items": [
                    replacement_item_payload(),
                ],
            },
        )
    )

    assert isinstance(
        request.items[0],
        ReplaceResponseItem,
    )


def test_rejects_empty_items() -> None:
    with pytest.raises(
        ValidationError,
    ):
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "items": [],
            },
        )


def test_rejects_duplicate_question_codes() -> None:
    first = reissue_item_payload()
    second = reissue_item_payload()
    second["question_code"] = (
        first["question_code"]
    )

    with pytest.raises(
        ValidationError,
        match="문항 코드",
    ):
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "items": [
                    first,
                    second,
                ],
            },
        )


def test_rejects_duplicate_recording_ids() -> None:
    first = replacement_item_payload()
    second = replacement_item_payload()
    second["question_code"] = (
        "orientation_month"
    )
    second["variant_id"] = (
        "orientation-month-fixed-v1"
    )
    second["recording_id"] = (
        first["recording_id"]
    )

    with pytest.raises(
        ValidationError,
        match="recording_id",
    ):
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "items": [
                    first,
                    second,
                ],
            },
        )


def test_rejects_duplicate_response_ids() -> None:
    first = replacement_item_payload()
    second = replacement_item_payload()
    second["question_code"] = (
        "orientation_month"
    )
    second["variant_id"] = (
        "orientation-month-fixed-v1"
    )
    second["response_id"] = (
        first["response_id"]
    )

    with pytest.raises(
        ValidationError,
        match="response_id",
    ):
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "items": [
                    first,
                    second,
                ],
            },
        )


def test_accepts_mixed_retry_actions() -> None:
    replacement = replacement_item_payload()
    reissue = reissue_item_payload()

    reissue["question_code"] = (
        "orientation_month"
    )

    request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "INCOMPLETE_ASSESSMENT"
                ),
                "items": [
                    replacement,
                    reissue,
                ],
            },
        )
    )

    assert len(request.items) == 2
    assert isinstance(
        request.items[0],
        ReplaceResponseItem,
    )
    assert isinstance(
        request.items[1],
        ReissueAudioUrlItem,
    )
    assert [
        item.retry_action
        for item in request.items
    ] == [
        "REPLACE_RESPONSE",
        "REISSUE_AUDIO_URL",
    ]


def test_reissue_rejects_replacement_fields() -> None:
    payload = reissue_item_payload()
    payload["stt"] = {
        "status": "success",
        "raw_transcript": "2026년",
    }

    with pytest.raises(
        ValidationError,
    ):
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "items": [payload],
            },
        )


def test_replace_requires_stt() -> None:
    payload = replacement_item_payload()
    del payload["stt"]

    with pytest.raises(
        ValidationError,
    ):
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "items": [payload],
            },
        )


def test_replace_validates_stt_relationship() -> None:
    payload = replacement_item_payload()
    payload["stt"] = {
        "status": "success",
        "raw_transcript": "",
    }

    with pytest.raises(
        ValidationError,
    ):
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "items": [payload],
            },
        )


def test_rejects_unknown_fields() -> None:
    payload = deepcopy(
        replacement_item_payload(),
    )
    payload["unknown_field"] = True

    with pytest.raises(
        ValidationError,
    ):
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "INCOMPLETE_ASSESSMENT"
                ),
                "items": [payload],
            },
        )