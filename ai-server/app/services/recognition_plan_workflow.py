from uuid import UUID

from fastapi.concurrency import run_in_threadpool

from app.api.schemas.common import (
    QuestionAnalysisResult,
    RetryReasonCode,
)
from app.api.schemas.recognition_plan import (
    RecognitionPlanCompletedResponse,
    RecognitionPlanNeedsRetryResponse,
    RecognitionPlanRequest,
)
from app.audio.downloader import (
    AudioDownloadError,
    SignedAudioDownloader,
)
from app.audio.preprocessing import (
    AudioPreprocessingError,
    preprocess_audio,
)
from app.audio.vad import (
    VadService,
    VadStatus,
)
from app.contracts.models import ContractBundle
from app.scoring.memory_failure import (
    MemoryFailureCompletedDecision,
    MemoryFailureNeedsRetryDecision,
    MemoryFailureScoringService,
)
from app.services.recognition_plan import (
    RecognitionPlanCompletedDecision,
    RecognitionPlanService,
)

RecognitionPlanWorkflowResponse = (
    RecognitionPlanCompletedResponse
    | RecognitionPlanNeedsRetryResponse
)


class RecognitionPlanWorkflow:
    def __init__(
        self,
        *,
        contract_bundle: ContractBundle,
        audio_downloader: SignedAudioDownloader,
        vad_service: VadService,
    ) -> None:
        self._audio_downloader = audio_downloader
        self._vad_service = vad_service
        self._recognition_plan_service = (
            RecognitionPlanService
            .from_contract_bundle(
                contract_bundle,
            )
        )
        self._memory_failure_service = (
            MemoryFailureScoringService
            .from_contract_bundle(
                contract_bundle,
            )
        )

    async def execute(
        self,
        *,
        assessment_id: UUID,
        request: RecognitionPlanRequest,
    ) -> RecognitionPlanWorkflowResponse:
        response = request.response
        audio_resource = response.audio

        try:
            downloaded_audio = (
                await self._audio_downloader.download(
                    signed_url=str(
                        audio_resource.signed_url,
                    ),
                    expires_at=(
                        audio_resource.expires_at
                    ),
                    declared_content_type=(
                        audio_resource.content_type
                    ),
                    declared_size_bytes=(
                        audio_resource.size_bytes
                    ),
                    expected_sha256=(
                        audio_resource.sha256
                    ),
                )
            )

            processed_audio = await run_in_threadpool(
                preprocess_audio,
                content=downloaded_audio.content,
                content_type=(
                    downloaded_audio.content_type
                ),
            )
        except AudioDownloadError as error:
            return self._needs_retry_response(
                assessment_id=assessment_id,
                request=request,
                reason_code=error.reason_code.value,
            )
        except AudioPreprocessingError as error:
            return self._needs_retry_response(
                assessment_id=assessment_id,
                request=request,
                reason_code=error.reason_code.value,
            )

        vad_result = await run_in_threadpool(
            self._vad_service.detect_response_onset,
            audio=processed_audio,
            prompt_end_to_recording_start_ms=(
                response
                .timing
                .prompt_end_to_recording_start_ms
            ),
        )
        vad_status = vad_result.status.value

        memory_failure_decision = (
            self._memory_failure_service.score(
                question_code=response.question_code,
                stt=response.stt,
                vad_status=vad_status,
            )
        )

        if isinstance(
            memory_failure_decision,
            MemoryFailureNeedsRetryDecision,
        ):
            return self._needs_retry_response(
                assessment_id=assessment_id,
                request=request,
                reason_code=(
                    memory_failure_decision.reason_code
                ),
            )

        if not isinstance(
            memory_failure_decision,
            MemoryFailureCompletedDecision,
        ):
            raise TypeError(
                "지원하지 않는 기억 실패 판정 결과입니다.",
            )

        plan_decision = (
            self._recognition_plan_service.create_plan(
                stt=response.stt,
                vad_status=vad_status,
            )
        )

        if not isinstance(
            plan_decision,
            RecognitionPlanCompletedDecision,
        ):
            raise RuntimeError(
                "기억 판정과 재인 계획 상태가 "
                "일치하지 않습니다.",
            )

        if (
            plan_decision.recalled_units
            != memory_failure_decision
            .recognized_memory_units
        ):
            raise RuntimeError(
                "기억 단위 추출 결과가 "
                "서로 일치하지 않습니다.",
            )

        if vad_result.status != VadStatus.SPEECH_DETECTED:
            raise RuntimeError(
                "완료 응답에는 발화가 탐지되어야 합니다.",
            )

        q11_result = QuestionAnalysisResult(
            question_code=response.question_code,
            administration_status="administered",
            recording_id=response.recording_id,
            response_id=response.response_id,
            vad_status="speech_detected",
            scoring_status="not_scored",
            answer_status=None,
            wrong_event=(
                memory_failure_decision.wrong_event
            ),
            wrong_event_reason=(
                memory_failure_decision
                .wrong_event_reason
            ),
            response_delay_ms=(
                vad_result.response_delay_ms
            ),
            recognized_memory_units=(
                plan_decision.recalled_units
            ),
        )

        return RecognitionPlanCompletedResponse(
            assessment_id=assessment_id,
            status="completed",
            question_set_version=(
                request.question_set_version
            ),
            wrong_event_rule_version=(
                request.wrong_event_rule_version
            ),
            recalled_units=(
                plan_decision.recalled_units
            ),
            next_question_codes=list(
                plan_decision.next_question_codes,
            ),
            q11_result=q11_result,
        )

    @staticmethod
    def _needs_retry_response(
        *,
        assessment_id: UUID,
        request: RecognitionPlanRequest,
        reason_code: RetryReasonCode,
    ) -> RecognitionPlanNeedsRetryResponse:
        return RecognitionPlanNeedsRetryResponse(
            assessment_id=assessment_id,
            status="needs_retry",
            question_set_version=(
                request.question_set_version
            ),
            wrong_event_rule_version=(
                request.wrong_event_rule_version
            ),
            reason_code=reason_code,
            retryable=True,
            retry_question_codes=[
                "memory_delayed_free_recall",
            ],
        )