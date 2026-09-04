from datetime import UTC, datetime
from pathlib import Path
from uuid import (
    NAMESPACE_URL,
    uuid4,
    uuid5,
)

import pytest

from app.api.schemas.analysis import (
    AdministeredQuestionResponse,
    AnalysisCreateRequest,
    AnalysisRetryRequest,
)
from app.contracts.loader import (
    load_contract_bundle,
)
from app.repositories.analysis import (
    AnalysisStatus,
    StoredAnalysis,
)
from app.services.analysis_retry import (
    AnalysisRetryService,
    AnalysisRetryValidationError,
)


@pytest.fixture
def service() -> AnalysisRetryService:
    project_root = (
        Path(__file__).resolve().parents[1]
    )
    bundle = load_contract_bundle(
        project_root / "contracts",
    )

    return (
        AnalysisRetryService
        .from_contract_bundle(bundle)
    )


def test_reissues_only_audio_resource(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    original_response = _response(
        original_request,
        "orientation_year",
    )
    analysis = _stored_analysis(
        original_request,
        reason_code="AUDIO_URL_EXPIRED",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        ),
    )
    retry_request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "items": [
                    {
                        "question_code": (
                            "orientation_year"
                        ),
                        "retry_action": (
                            "REISSUE_AUDIO_URL"
                        ),
                        "recording_id": str(
                            original_response
                            .recording_id
                        ),
                        "response_id": str(
                            original_response
                            .response_id
                        ),
                        "audio": _audio_payload(
                            "new-year.wav",
                        ),
                    },
                ],
            },
        )
    )

    updated = service.merge_request(
        analysis=analysis,
        retry_request=retry_request,
    )
    updated_response = _response(
        updated,
        "orientation_year",
    )

    assert (
        updated_response.recording_id
        == original_response.recording_id
    )
    assert (
        updated_response.response_id
        == original_response.response_id
    )
    assert (
        str(updated_response.audio.signed_url)
        != str(original_response.audio.signed_url)
    )
    assert (
        updated_response.stt
        == original_response.stt
    )
    assert (
        updated_response.timing
        == original_response.timing
    )
    assert updated.responses[1:] == (
        original_request.responses[1:]
    )


def test_replaces_full_response(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    original_response = _response(
        original_request,
        "orientation_year",
    )
    analysis = _stored_analysis(
        original_request,
        reason_code="UNSCORABLE_STT",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "required_action": (
                    "REPLACE_RESPONSE"
                ),
            },
        ),
    )
    new_recording_id = uuid4()
    new_response_id = uuid4()
    retry_request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "items": [
                    {
                        "question_code": (
                            "orientation_year"
                        ),
                        "retry_action": (
                            "REPLACE_RESPONSE"
                        ),
                        "variant_id": (
                            original_response.variant_id
                        ),
                        "recording_id": str(
                            new_recording_id,
                        ),
                        "response_id": str(
                            new_response_id,
                        ),
                        "audio": _audio_payload(
                            "replacement-year.wav",
                        ),
                        "stt": {
                            "status": "success",
                            "raw_transcript": (
                                "2026년"
                            ),
                        },
                        "timing": {
                            "prompt_end_to_recording_start_ms": 80,
                            "recording_duration_ms": 1400,
                        },
                    },
                ],
            },
        )
    )

    updated = service.merge_request(
        analysis=analysis,
        retry_request=retry_request,
    )
    updated_response = _response(
        updated,
        "orientation_year",
    )

    assert (
        updated_response.recording_id
        == new_recording_id
    )
    assert (
        updated_response.response_id
        == new_response_id
    )
    assert (
        updated_response.stt.raw_transcript
        == "2026년"
    )
    assert (
        updated_response.timing
        .recording_duration_ms
        == 1400
    )
    assert updated.responses[1:] == (
        original_request.responses[1:]
    )


def test_rejects_non_retryable_state(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    analysis = _stored_analysis(
        original_request,
        reason_code="AUDIO_URL_EXPIRED",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        ),
        status=AnalysisStatus.PENDING,
    )
    retry_request = _reissue_request(
        original_request,
    )

    with pytest.raises(
        AnalysisRetryValidationError,
        match="needs_retry",
    ):
        service.merge_request(
            analysis=analysis,
            retry_request=retry_request,
        )


def test_rejects_reason_code_mismatch(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    analysis = _stored_analysis(
        original_request,
        reason_code="AUDIO_DOWNLOAD_FAILED",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_DOWNLOAD_FAILED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        ),
    )
    retry_request = _reissue_request(
        original_request,
        reason_code="AUDIO_URL_EXPIRED",
    )

    with pytest.raises(
        AnalysisRetryValidationError,
        match="사유 코드",
    ):
        service.merge_request(
            analysis=analysis,
            retry_request=retry_request,
        )


def test_requires_exact_retry_question_set(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    analysis = _stored_analysis(
        original_request,
        reason_code="AUDIO_URL_EXPIRED",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
            {
                "question_code": (
                    "orientation_month"
                ),
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        ),
    )
    retry_request = _reissue_request(
        original_request,
    )

    with pytest.raises(
        AnalysisRetryValidationError,
        match="문항 구성",
    ):
        service.merge_request(
            analysis=analysis,
            retry_request=retry_request,
        )


def test_rejects_wrong_retry_action(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    original_response = _response(
        original_request,
        "orientation_year",
    )
    analysis = _stored_analysis(
        original_request,
        reason_code="AUDIO_DOWNLOAD_FAILED",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_DOWNLOAD_FAILED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        ),
    )
    retry_request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "AUDIO_DOWNLOAD_FAILED"
                ),
                "items": [
                    {
                        "question_code": (
                            "orientation_year"
                        ),
                        "retry_action": (
                            "REPLACE_RESPONSE"
                        ),
                        "variant_id": (
                            original_response.variant_id
                        ),
                        "recording_id": str(
                            uuid4(),
                        ),
                        "response_id": str(
                            uuid4(),
                        ),
                        "audio": _audio_payload(
                            "replacement.wav",
                        ),
                        "stt": {
                            "status": "success",
                            "raw_transcript": (
                                "2026년"
                            ),
                        },
                        "timing": {
                            "prompt_end_to_recording_start_ms": 100,
                            "recording_duration_ms": 1000,
                        },
                    },
                ],
            },
        )
    )

    with pytest.raises(
        AnalysisRetryValidationError,
        match="작업",
    ):
        service.merge_request(
            analysis=analysis,
            retry_request=retry_request,
        )


def test_reissue_requires_same_identifiers(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    original_response = _response(
        original_request,
        "orientation_year",
    )
    analysis = _stored_analysis(
        original_request,
        reason_code="AUDIO_URL_EXPIRED",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        ),
    )
    retry_request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "items": [
                    {
                        "question_code": (
                            "orientation_year"
                        ),
                        "retry_action": (
                            "REISSUE_AUDIO_URL"
                        ),
                        "recording_id": str(
                            uuid4(),
                        ),
                        "response_id": str(
                            original_response
                            .response_id
                        ),
                        "audio": _audio_payload(
                            "new-year.wav",
                        ),
                    },
                ],
            },
        )
    )

    with pytest.raises(
        AnalysisRetryValidationError,
        match="recording_id",
    ):
        service.merge_request(
            analysis=analysis,
            retry_request=retry_request,
        )


def test_replacement_requires_new_identifiers(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    original_response = _response(
        original_request,
        "orientation_year",
    )
    analysis = _stored_analysis(
        original_request,
        reason_code="UNSCORABLE_STT",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "required_action": (
                    "REPLACE_RESPONSE"
                ),
            },
        ),
    )
    retry_request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "items": [
                    {
                        "question_code": (
                            "orientation_year"
                        ),
                        "retry_action": (
                            "REPLACE_RESPONSE"
                        ),
                        "variant_id": (
                            original_response.variant_id
                        ),
                        "recording_id": str(
                            original_response
                            .recording_id
                        ),
                        "response_id": str(
                            uuid4(),
                        ),
                        "audio": _audio_payload(
                            "replacement.wav",
                        ),
                        "stt": {
                            "status": "success",
                            "raw_transcript": (
                                "2026년"
                            ),
                        },
                        "timing": {
                            "prompt_end_to_recording_start_ms": 100,
                            "recording_duration_ms": 1000,
                        },
                    },
                ],
            },
        )
    )

    with pytest.raises(
        AnalysisRetryValidationError,
        match="새로운 recording_id",
    ):
        service.merge_request(
            analysis=analysis,
            retry_request=retry_request,
        )


def test_rejects_variant_change(
    service: AnalysisRetryService,
) -> None:
    original_request = _valid_request()
    analysis = _stored_analysis(
        original_request,
        reason_code="UNSCORABLE_STT",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "required_action": (
                    "REPLACE_RESPONSE"
                ),
            },
        ),
    )
    retry_request = (
        AnalysisRetryRequest.model_validate(
            {
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "items": [
                    {
                        "question_code": (
                            "orientation_year"
                        ),
                        "retry_action": (
                            "REPLACE_RESPONSE"
                        ),
                        "variant_id": (
                            "wrong-variant"
                        ),
                        "recording_id": str(
                            uuid4(),
                        ),
                        "response_id": str(
                            uuid4(),
                        ),
                        "audio": _audio_payload(
                            "replacement.wav",
                        ),
                        "stt": {
                            "status": "success",
                            "raw_transcript": (
                                "2026년"
                            ),
                        },
                        "timing": {
                            "prompt_end_to_recording_start_ms": 100,
                            "recording_duration_ms": 1000,
                        },
                    },
                ],
            },
        )
    )

    with pytest.raises(
        AnalysisRetryValidationError,
        match="variant_id",
    ):
        service.merge_request(
            analysis=analysis,
            retry_request=retry_request,
        )


def _valid_request() -> AnalysisCreateRequest:
    project_root = (
        Path(__file__).resolve().parents[1]
    )
    bundle = load_contract_bundle(
        project_root / "contracts",
    )

    responses = []

    for question in sorted(
        bundle.cist.questions,
        key=lambda item: item.order,
    ):
        if (
            question.administration_mode
            == "conditional"
        ):
            responses.append(
                {
                    "question_code": (
                        question.question_code
                    ),
                    "variant_id": (
                        question.variant_id
                    ),
                    "administration_status": (
                        "not_applicable"
                    ),
                },
            )
            continue

        responses.append(
            _administered_payload(
                question.question_code,
                question.variant_id,
            ),
        )

    return AnalysisCreateRequest.model_validate(
        {
            "analysis_id": str(uuid4()),
            "assessment_id": str(uuid4()),
            "question_set_version": (
                "cist-v1"
            ),
            "wrong_event_rule_version": (
                "wrong-event-v1"
            ),
            "assessment_local_date": (
                "2026-09-05"
            ),
            "timezone": "Asia/Seoul",
            "stt_config": {
                "provider": "google",
                "api_version": "v2",
                "location": "us",
                "model": "chirp_3",
                "language": "ko-KR",
                "automatic_punctuation": True,
            },
            "recognition_plan": {
                "source_question_code": (
                    "memory_delayed_free_recall"
                ),
                "recalled_units": {
                    "person": True,
                    "transport": True,
                    "place": True,
                    "time": True,
                    "activity": True,
                },
                "selected_question_codes": [],
            },
            "responses": responses,
        },
    )


def _administered_payload(
    question_code: str,
    variant_id: str,
) -> dict:
    return {
        "question_code": question_code,
        "variant_id": variant_id,
        "administration_status": (
            "administered"
        ),
        "recording_id": str(
            uuid5(
                NAMESPACE_URL,
                f"recording:{question_code}",
            ),
        ),
        "response_id": str(
            uuid5(
                NAMESPACE_URL,
                f"response:{question_code}",
            ),
        ),
        "audio": _audio_payload(
            f"{question_code}.wav",
        ),
        "stt": {
            "status": "success",
            "raw_transcript": "테스트 답변",
        },
        "timing": {
            "prompt_end_to_recording_start_ms": 100,
            "recording_duration_ms": 1000,
        },
    }


def _audio_payload(
    filename: str,
) -> dict:
    return {
        "signed_url": (
            "https://storage.example/"
            f"{filename}?signature=test"
        ),
        "expires_at": (
            "2099-01-01T00:00:00Z"
        ),
        "content_type": "audio/wav",
        "size_bytes": 1024,
    }


def _stored_analysis(
    request: AnalysisCreateRequest,
    *,
    reason_code: str,
    retry_items: tuple[dict, ...],
    status: AnalysisStatus = (
        AnalysisStatus.NEEDS_RETRY
    ),
) -> StoredAnalysis:
    timestamp = datetime.now(UTC)

    return StoredAnalysis(
        analysis_id=request.analysis_id,
        assessment_id=request.assessment_id,
        status=status,
        request_body=request.model_dump(
            mode="json",
        ),
        retryable=(
            status
            == AnalysisStatus.NEEDS_RETRY
        ),
        reason_code=reason_code,
        retry_items=retry_items,
        result_body=None,
        created_at=timestamp,
        updated_at=timestamp,
    )


def _response(
    request: AnalysisCreateRequest,
    question_code: str,
) -> AdministeredQuestionResponse:
    for response in request.responses:
        if (
            response.question_code
            == question_code
        ):
            assert isinstance(
                response,
                AdministeredQuestionResponse,
            )
            return response

    raise AssertionError(
        f"문항을 찾을 수 없습니다: {question_code}",
    )


def _reissue_request(
    request: AnalysisCreateRequest,
    *,
    reason_code: str = (
        "AUDIO_URL_EXPIRED"
    ),
) -> AnalysisRetryRequest:
    response = _response(
        request,
        "orientation_year",
    )

    return AnalysisRetryRequest.model_validate(
        {
            "reason_code": reason_code,
            "items": [
                {
                    "question_code": (
                        "orientation_year"
                    ),
                    "retry_action": (
                        "REISSUE_AUDIO_URL"
                    ),
                    "recording_id": str(
                        response.recording_id,
                    ),
                    "response_id": str(
                        response.response_id,
                    ),
                    "audio": _audio_payload(
                        "new-year.wav",
                    ),
                },
            ],
        },
    )