import asyncio
from pathlib import Path
from types import SimpleNamespace
from uuid import UUID, uuid4

import numpy as np

from app.audio.downloader import (
    AudioDownloadError,
    AudioDownloadReason,
    DownloadedAudio,
)
from app.audio.preprocessing import (
    ProcessedAudio,
)
from app.audio.vad import (
    VadResult,
    VadStatus,
)
from app.contracts.loader import (
    load_contract_bundle,
)
from app.contracts.models import ContractBundle
from app.core.config import PROJECT_ROOT
from app.inference.fusion import (
    FusionInferenceResult,
)
from app.repositories.analysis import (
    SQLiteAnalysisRepository,
)
from app.services.analysis_worker import (
    AnalysisCompleted,
    AnalysisNeedsRetry,
)
from app.services.session_analysis import (
    SessionAnalysisProcessor,
)


class FakeDownloader:
    def __init__(
        self,
        *,
        failure_call: int | None = None,
        failure: AudioDownloadError | None = None,
    ) -> None:
        self.call_count = 0
        self.failure_call = failure_call
        self.failure = failure

    async def download(
        self,
        **_kwargs,
    ) -> DownloadedAudio:
        self.call_count += 1

        if (
            self.failure_call
            == self.call_count
            and self.failure is not None
        ):
            raise self.failure

        return DownloadedAudio(
            content=b"abc",
            content_type="audio/wav",
            size_bytes=3,
            sha256="0" * 64,
        )


class FakeVadService:
    def __init__(
        self,
        *,
        no_speech_call: int | None = None,
    ) -> None:
        self.call_count = 0
        self.no_speech_call = no_speech_call

    def detect_response_onset(
        self,
        *,
        audio: ProcessedAudio,
        prompt_end_to_recording_start_ms: int,
    ) -> VadResult:
        del audio
        self.call_count += 1

        if (
            self.call_count
            == self.no_speech_call
        ):
            return VadResult(
                status=(
                    VadStatus.NO_SPEECH_DETECTED
                ),
                raw_onset_ms=None,
                corrected_onset_ms=None,
                prompt_end_to_recording_start_ms=(
                    prompt_end_to_recording_start_ms
                ),
                response_delay_ms=None,
                vad_config_version="vad-v1",
            )

        return VadResult(
            status=VadStatus.SPEECH_DETECTED,
            raw_onset_ms=900,
            corrected_onset_ms=900,
            prompt_end_to_recording_start_ms=(
                prompt_end_to_recording_start_ms
            ),
            response_delay_ms=1000,
            vad_config_version="vad-v1",
        )


class FakeAstService:
    def __init__(self) -> None:
        self.received_codes: tuple[
            str,
            ...,
        ] = ()

    def infer(self, clips):
        self.received_codes = tuple(
            clip.question_code
            for clip in clips
        )
        return SimpleNamespace(
            dementia_logit=-0.25,
        )


class FakeKcElectraService:
    def __init__(self) -> None:
        self.received_codes: tuple[
            str,
            ...,
        ] = ()

    def infer(self, clips):
        self.received_codes = tuple(
            clip.question_code
            for clip in clips
        )
        return SimpleNamespace(
            dementia_logit=0.5,
        )


class FakeFusionService:
    def __init__(self) -> None:
        self.received_features = None

    def infer(
        self,
        features,
    ) -> FusionInferenceResult:
        self.received_features = features

        return FusionInferenceResult(
            model_version=(
                "final_fusion_lr_"
                "21subjects_core4_ast_v1"
            ),
            model_score=0.8,
            decision_threshold=0.5,
            threshold_version=(
                "fusion-threshold-v1"
            ),
            risk_flag=True,
            features=features,
        )


def fake_preprocess_audio(
    *,
    content: bytes,
    content_type: str,
) -> ProcessedAudio:
    del content

    return ProcessedAudio(
        waveform=np.zeros(
            16_000,
            dtype=np.float32,
        ),
        sample_rate=16_000,
        duration_ms=1000,
        source_content_type=content_type,
    )


def load_contracts() -> ContractBundle:
    return load_contract_bundle(
        PROJECT_ROOT / "contracts",
    )


def create_request_body(
    contracts: ContractBundle,
    *,
    stt_status_by_code: (
        dict[str, str] | None
    ) = None,
) -> dict:
    stt_status_by_code = (
        stt_status_by_code or {}
    )
    analysis_id = uuid4()
    assessment_id = uuid4()
    responses: list[dict] = []

    for question in sorted(
        contracts.cist.questions,
        key=lambda item: item.order,
    ):
        question_code = question.question_code

        if (
            question.administration_mode
            == "conditional"
        ):
            responses.append(
                {
                    "question_code": (
                        question_code
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

        stt_status = (
            stt_status_by_code.get(
                question_code,
                "success",
            )
        )

        if question_code in {
            "memory_registration_first",
            "memory_registration_second",
            "memory_delayed_free_recall",
        }:
            transcript = (
                "민수 자전거 공원 11시 야구"
            )
        else:
            transcript = "대답"

        if stt_status != "success":
            transcript = None

        responses.append(
            {
                "question_code": question_code,
                "variant_id": (
                    question.variant_id
                ),
                "administration_status": (
                    "administered"
                ),
                "recording_id": str(
                    uuid4(),
                ),
                "response_id": str(
                    uuid4(),
                ),
                "audio": {
                    "signed_url": (
                        "https://storage.example/"
                        f"{question_code}.wav"
                        "?signature=test"
                    ),
                    "expires_at": (
                        "2099-01-01T00:00:00Z"
                    ),
                    "content_type": (
                        "audio/wav"
                    ),
                    "size_bytes": 3,
                },
                "stt": {
                    "status": stt_status,
                    "raw_transcript": (
                        transcript
                    ),
                },
                "timing": {
                    "prompt_end_to_recording_start_ms": 100,
                    "recording_duration_ms": 1000,
                },
            },
        )

    return {
        "analysis_id": str(analysis_id),
        "assessment_id": str(assessment_id),
        "question_set_version": "cist-v1",
        "wrong_event_rule_version": (
            "wrong-event-v1"
        ),
        "assessment_local_date": (
            "2026-09-01"
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
    }


def create_stored_analysis(
    tmp_path: Path,
    request_body: dict,
):
    repository = SQLiteAnalysisRepository(
        tmp_path / "analyses.sqlite3",
    )
    analysis_id = UUID(
        request_body["analysis_id"],
    )
    assessment_id = UUID(
        request_body["assessment_id"],
    )

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=assessment_id,
        request_body=request_body,
    )

    return repository.mark_processing(
        analysis_id,
    )


def create_processor(
    contracts: ContractBundle,
    *,
    downloader=None,
    vad_service=None,
):
    ast_service = FakeAstService()
    kcelectra_service = (
        FakeKcElectraService()
    )
    fusion_service = FakeFusionService()

    processor = SessionAnalysisProcessor(
        contracts=contracts,
        audio_downloader=(
            downloader or FakeDownloader()
        ),
        vad_service=(
            vad_service
            or FakeVadService()
        ),
        ast_service=ast_service,
        kcelectra_service=(
            kcelectra_service
        ),
        fusion_service=fusion_service,
        audio_preprocessor=(
            fake_preprocess_audio
        ),
    )

    return (
        processor,
        ast_service,
        kcelectra_service,
        fusion_service,
    )


def test_completes_full_session_pipeline(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        contracts = load_contracts()
        request_body = create_request_body(
            contracts,
        )
        analysis = create_stored_analysis(
            tmp_path,
            request_body,
        )
        (
            processor,
            ast_service,
            kcelectra_service,
            fusion_service,
        ) = create_processor(
            contracts,
        )

        outcome = await processor.process(
            analysis,
        )

        assert isinstance(
            outcome,
            AnalysisCompleted,
        )

        result = outcome.result_body

        assert result["model_score"] == 0.8
        assert result[
            "decision_threshold"
        ] == 0.5
        assert result[
            "threshold_version"
        ] == "fusion-threshold-v1"
        assert result["risk_flag"] is True
        assert len(
            result["question_results"],
        ) == 17

        assert result["features"][
            "ast_logit"
        ] == -0.25
        assert result["features"][
            "kcelectra_logit"
        ] == 0.5
        assert result["features"][
            "category_balanced_median_delay"
        ] == 1.0

        assert len(
            ast_service.received_codes,
        ) == 12
        assert (
            ast_service.received_codes
            == kcelectra_service.received_codes
        )
        assert (
            fusion_service.received_features
            is not None
        )

    asyncio.run(scenario())


def test_no_response_requires_retry(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        contracts = load_contracts()
        request_body = create_request_body(
            contracts,
        )
        analysis = create_stored_analysis(
            tmp_path,
            request_body,
        )
        (
            processor,
            _ast,
            _kcelectra,
            _fusion,
        ) = create_processor(
            contracts,
            vad_service=FakeVadService(
                no_speech_call=1,
            ),
        )

        outcome = await processor.process(
            analysis,
        )

        assert isinstance(
            outcome,
            AnalysisNeedsRetry,
        )
        assert outcome.reason_code == (
            "INCOMPLETE_ASSESSMENT"
        )
        assert outcome.retry_items[0] == {
            "question_code": (
                "orientation_year"
            ),
            "reason_code": (
                "INCOMPLETE_ASSESSMENT"
            ),
            "required_action": (
                "REPLACE_RESPONSE"
            ),
        }

    asyncio.run(scenario())


def test_place_no_response_is_not_wrong_event(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        contracts = load_contracts()
        request_body = create_request_body(
            contracts,
        )
        analysis = create_stored_analysis(
            tmp_path,
            request_body,
        )
        (
            processor,
            _ast,
            _kcelectra,
            _fusion,
        ) = create_processor(
            contracts,
            vad_service=FakeVadService(
                # Q05는 다섯 번째 시행 문항이다.
                no_speech_call=5,
            ),
        )

        outcome = await processor.process(
            analysis,
        )

        assert isinstance(
            outcome,
            AnalysisCompleted,
        )

        place_result = next(
            result
            for result in (
                outcome.result_body[
                    "question_results"
                ]
            )
            if result["question_code"]
            == "orientation_place"
        )

        assert place_result[
            "vad_status"
        ] == "no_response"
        assert place_result[
            "scoring_status"
        ] == "not_scored"
        assert place_result[
            "wrong_event"
        ] is None
        assert place_result[
            "response_delay_ms"
        ] is None

    asyncio.run(scenario())


def test_failed_stt_requires_retry(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        contracts = load_contracts()
        request_body = create_request_body(
            contracts,
            stt_status_by_code={
                "orientation_year": "failed",
            },
        )
        analysis = create_stored_analysis(
            tmp_path,
            request_body,
        )
        (
            processor,
            _ast,
            _kcelectra,
            _fusion,
        ) = create_processor(
            contracts,
        )

        outcome = await processor.process(
            analysis,
        )

        assert isinstance(
            outcome,
            AnalysisNeedsRetry,
        )
        assert outcome.reason_code == (
            "UNSCORABLE_STT"
        )
        assert outcome.retry_items[0][
            "required_action"
        ] == "REPLACE_RESPONSE"

    asyncio.run(scenario())


def test_expired_url_requests_reissue(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        contracts = load_contracts()
        request_body = create_request_body(
            contracts,
        )
        analysis = create_stored_analysis(
            tmp_path,
            request_body,
        )
        downloader = FakeDownloader(
            failure_call=1,
            failure=AudioDownloadError(
                reason_code=(
                    AudioDownloadReason
                    .AUDIO_URL_EXPIRED
                ),
                message="expired",
                retryable=True,
            ),
        )
        (
            processor,
            _ast,
            _kcelectra,
            _fusion,
        ) = create_processor(
            contracts,
            downloader=downloader,
        )

        outcome = await processor.process(
            analysis,
        )

        assert isinstance(
            outcome,
            AnalysisNeedsRetry,
        )
        assert outcome.reason_code == (
            "AUDIO_URL_EXPIRED"
        )
        assert outcome.retry_items[0] == {
            "question_code": (
                "orientation_year"
            ),
            "reason_code": (
                "AUDIO_URL_EXPIRED"
            ),
            "required_action": (
                "REISSUE_AUDIO_URL"
            ),
        }

    asyncio.run(scenario())


def test_mixed_retry_reasons_return_all_actions(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        contracts = load_contracts()
        request_body = create_request_body(
            contracts,
        )
        analysis = create_stored_analysis(
            tmp_path,
            request_body,
        )

        downloader = FakeDownloader(
            failure_call=2,
            failure=AudioDownloadError(
                reason_code=(
                    AudioDownloadReason
                    .AUDIO_URL_EXPIRED
                ),
                message="expired",
                retryable=True,
            ),
        )

        (
            processor,
            _ast,
            _kcelectra,
            _fusion,
        ) = create_processor(
            contracts,
            downloader=downloader,
            vad_service=FakeVadService(
                no_speech_call=1,
            ),
        )

        outcome = await processor.process(
            analysis,
        )

        assert isinstance(
            outcome,
            AnalysisNeedsRetry,
        )

        assert outcome.reason_code == (
            "INCOMPLETE_ASSESSMENT"
        )

        assert outcome.retry_items == (
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "INCOMPLETE_ASSESSMENT"
                ),
                "required_action": (
                    "REPLACE_RESPONSE"
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
        )

    asyncio.run(scenario())
