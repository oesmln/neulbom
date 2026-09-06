from collections.abc import Callable
from dataclasses import dataclass
from typing import Literal

from fastapi.concurrency import (
    run_in_threadpool,
)
from pydantic import ValidationError

from app.api.schemas.analysis import (
    AdministeredQuestionResponse,
    AnalysisCreateRequest,
    NotApplicableQuestionResponse,
)
from app.api.schemas.common import (
    MemoryUnitMap,
    QuestionAnalysisResult,
)
from app.audio.downloader import (
    AudioDownloadError,
    AudioDownloadReason,
    SignedAudioDownloader,
)
from app.audio.preprocessing import (
    AudioPreprocessingError,
    ProcessedAudio,
    preprocess_audio,
)
from app.audio.vad import (
    VadService,
    VadStatus,
)
from app.contracts.models import (
    ContractBundle,
    QuestionDefinition,
)
from app.inference.ast import (
    AstClipInput,
    AstInferenceError,
    AstInferenceService,
)
from app.inference.fusion import (
    FusionFeatures,
    FusionInferenceError,
    FusionInferenceService,
)
from app.inference.kcelectra import (
    KcElectraClipInput,
    KcElectraInferenceError,
    KcElectraInferenceService,
)
from app.repositories.analysis import (
    AnalysisStatus,
    StoredAnalysis,
)
from app.scoring.aggregation import (
    WrongEventAggregationService,
    WrongEventObservation,
)
from app.scoring.memory_failure import (
    MemoryFailureCompletedDecision,
    MemoryFailureNeedsRetryDecision,
    MemoryFailureScoringService,
)
from app.scoring.objective import (
    ObjectiveScoringService,
)
from app.scoring.response_delay import (
    ResponseDelayAggregationService,
    ResponseDelayObservation,
)
from app.services.analysis_worker import (
    AnalysisCompleted,
    AnalysisModelUnavailableError,
    AnalysisNeedsRetry,
    AnalysisProcessingOutcome,
)
from app.services.assessment_completeness import (
    AssessmentCompletenessService,
)

AudioPreprocessor = Callable[
    ...,
    ProcessedAudio,
]

RetryAction = Literal[
    "REISSUE_AUDIO_URL",
    "REPLACE_RESPONSE",
]


@dataclass(frozen=True, slots=True)
class _RetryCandidate:
    question_code: str
    reason_code: str
    required_action: RetryAction

    def to_dict(self) -> dict[str, str]:
        return {
            "question_code": self.question_code,
            "reason_code": self.reason_code,
            "required_action": self.required_action,
        }


@dataclass(frozen=True, slots=True)
class _ProcessedQuestion:
    result: QuestionAnalysisResult
    audio: ProcessedAudio
    raw_transcript: str


class SessionAnalysisProcessor:
    """한 세션의 전체 AI 분석 파이프라인."""

    def __init__(
        self,
        *,
        contracts: ContractBundle,
        audio_downloader: SignedAudioDownloader,
        vad_service: VadService,
        ast_service: AstInferenceService,
        kcelectra_service: (
            KcElectraInferenceService
        ),
        fusion_service: FusionInferenceService,
        audio_preprocessor: AudioPreprocessor = (
            preprocess_audio
        ),
    ) -> None:
        self._audio_downloader = (
            audio_downloader
        )
        self._vad_service = vad_service
        self._ast_service = ast_service
        self._kcelectra_service = (
            kcelectra_service
        )
        self._fusion_service = fusion_service
        self._audio_preprocessor = (
            audio_preprocessor
        )

        self._completeness_service = (
            AssessmentCompletenessService
            .from_contract_bundle(contracts)
        )
        self._objective_service = (
            ObjectiveScoringService
            .from_contract_bundle(contracts)
        )
        self._memory_failure_service = (
            MemoryFailureScoringService
            .from_contract_bundle(contracts)
        )
        self._wrong_event_service = (
            WrongEventAggregationService
            .from_contract_bundle(contracts)
        )
        self._response_delay_service = (
            ResponseDelayAggregationService
            .from_contract_bundle(contracts)
        )

        ordered_questions = tuple(
            sorted(
                contracts.cist.questions,
                key=lambda item: item.order,
            )
        )
        self._question_order = tuple(
            question.question_code
            for question in ordered_questions
        )
        self._question_by_code = {
            question.question_code: question
            for question in ordered_questions
        }

        runtime_contract = (
            contracts
            .wrong_event
            .operational_runtime_contract
        )
        self._objective_codes = set(
            runtime_contract
            .objective_answer_policy
            .applies_to_question_codes
        )
        self._memory_failure_codes = set(
            runtime_contract
            .explicit_failure_event_policy
            .applies_to_question_codes
        )
        self._excluded_codes = set(
            runtime_contract
            .excluded_question_policy
            .question_codes
        )

    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisProcessingOutcome:
        if (
            analysis.status
            != AnalysisStatus.PROCESSING
        ):
            raise ValueError(
                "processing 상태의 분석 작업만 "
                "처리할 수 있습니다.",
            )

        request = self._parse_request(
            analysis,
        )
        self._completeness_service.validate(
            request,
        )

        responses_by_code = {
            response.question_code: response
            for response in request.responses
        }

        question_results: dict[
            str,
            QuestionAnalysisResult,
        ] = {}
        ast_inputs: list[AstClipInput] = []
        kcelectra_inputs: list[
            KcElectraClipInput
        ] = []
        retry_candidates: list[
            _RetryCandidate
        ] = []

        for question_code in (
            self._question_order
        ):
            response = responses_by_code[
                question_code
            ]
            question = self._question_by_code[
                question_code
            ]

            if isinstance(
                response,
                NotApplicableQuestionResponse,
            ):
                question_results[
                    question_code
                ] = self._not_applicable_result(
                    response,
                )
                continue

            processed = (
                await self._process_administered(
                    response=response,
                    question=question,
                    request=request,
                )
            )

            if isinstance(
                processed,
                _RetryCandidate,
            ):
                retry_candidates.append(
                    processed,
                )
                continue

            question_results[
                question_code
            ] = processed.result

            if question.feature_usage.ast:
                ast_inputs.append(
                    AstClipInput(
                        question_code=question_code,
                        audio=processed.audio,
                    ),
                )

            if (
                question
                .feature_usage
                .kcelectra
            ):
                kcelectra_inputs.append(
                    KcElectraClipInput(
                        question_code=question_code,
                        raw_transcript=(
                            processed.raw_transcript
                        ),
                    ),
                )

        if retry_candidates:
            retry_items = tuple(
                candidate.to_dict()
                for candidate in retry_candidates
            )

            return AnalysisNeedsRetry(
                reason_code=(
                    retry_candidates[
                        0
                    ].reason_code
                ),
                retry_items=retry_items,
            )

        ordered_results = tuple(
            question_results[question_code]
            for question_code
            in self._question_order
        )

        wrong_event_result = (
            self._wrong_event_service.aggregate(
                WrongEventObservation(
                    question_code=(
                        result.question_code
                    ),
                    wrong_event=(
                        result.wrong_event
                    ),
                )
                for result in ordered_results
            )
        )

        response_delay_result = (
            self._response_delay_service
            .aggregate(
                ResponseDelayObservation(
                    question_code=(
                        result.question_code
                    ),
                    response_delay_ms=(
                        result.response_delay_ms
                    ),
                )
                for result in ordered_results
            )
        )

        try:
            ast_result = await run_in_threadpool(
                self._ast_service.infer,
                tuple(ast_inputs),
            )
            kcelectra_result = (
                await run_in_threadpool(
                    self._kcelectra_service.infer,
                    tuple(kcelectra_inputs),
                )
            )

            fusion_features = FusionFeatures(
                ast_logit=(
                    ast_result.dementia_logit
                ),
                kcelectra_logit=(
                    kcelectra_result
                    .dementia_logit
                ),
                category_balanced_wrong_event_score=(
                    wrong_event_result
                    .category_balanced_wrong_event_score
                ),
                category_balanced_median_delay=(
                    response_delay_result
                    .category_balanced_median_delay
                ),
            )

            fusion_result = (
                await run_in_threadpool(
                    self._fusion_service.infer,
                    fusion_features,
                )
            )
        except (
            AstInferenceError,
            KcElectraInferenceError,
            FusionInferenceError,
        ) as error:
            raise AnalysisModelUnavailableError(
                "세션 분석 모델 추론에 "
                "실패했습니다.",
            ) from error

        result_body = {
            "question_set_version": (
                request.question_set_version
            ),
            "wrong_event_rule_version": (
                request.wrong_event_rule_version
            ),
            "model_version": (
                fusion_result.model_version
            ),
            "model_score": (
                fusion_result.model_score
            ),
            "decision_threshold": (
                fusion_result.decision_threshold
            ),
            "review_threshold": (
                fusion_result.review_threshold
            ),
            "threshold_version": (
                fusion_result.threshold_version
            ),
            "risk_flag": (
                fusion_result.risk_flag
            ),
            "risk_level": (
                fusion_result.risk_level.value
            ),
            "features": {
                "ast_logit": (
                    fusion_result
                    .features
                    .ast_logit
                ),
                "kcelectra_logit": (
                    fusion_result
                    .features
                    .kcelectra_logit
                ),
                "category_balanced_wrong_event_score": (
                    fusion_result
                    .features
                    .category_balanced_wrong_event_score
                ),
                "category_balanced_median_delay": (
                    fusion_result
                    .features
                    .category_balanced_median_delay
                ),
            },
            "question_results": [
                result.model_dump(
                    mode="json",
                )
                for result in ordered_results
            ],
        }

        return AnalysisCompleted(
            result_body=result_body,
        )

    def _parse_request(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisCreateRequest:
        try:
            request = (
                AnalysisCreateRequest
                .model_validate(
                    analysis.request_body,
                )
            )
        except ValidationError as error:
            raise RuntimeError(
                "저장된 분석 요청이 API 계약과 "
                "일치하지 않습니다.",
            ) from error

        if (
            request.analysis_id
            != analysis.analysis_id
        ):
            raise RuntimeError(
                "저장된 analysis_id가 "
                "작업 식별자와 일치하지 않습니다.",
            )

        if (
            request.assessment_id
            != analysis.assessment_id
        ):
            raise RuntimeError(
                "저장된 assessment_id가 "
                "작업 식별자와 일치하지 않습니다.",
            )

        return request

    async def _process_administered(
        self,
        *,
        response: AdministeredQuestionResponse,
        question: QuestionDefinition,
        request: AnalysisCreateRequest,
    ) -> _ProcessedQuestion | _RetryCandidate:
        try:
            downloaded_audio = (
                await self._audio_downloader.download(
                    signed_url=str(
                        response.audio.signed_url,
                    ),
                    expires_at=(
                        response.audio.expires_at
                    ),
                    declared_content_type=(
                        response.audio.content_type
                    ),
                    declared_size_bytes=(
                        response.audio.size_bytes
                    ),
                    expected_sha256=(
                        response.audio.sha256
                    ),
                )
            )
        except AudioDownloadError as error:
            return _RetryCandidate(
                question_code=(
                    response.question_code
                ),
                reason_code=(
                    error.reason_code.value
                ),
                required_action=(
                    _download_retry_action(
                        error,
                    )
                ),
            )

        try:
            processed_audio = (
                await run_in_threadpool(
                    self._audio_preprocessor,
                    content=(
                        downloaded_audio.content
                    ),
                    content_type=(
                        downloaded_audio
                        .content_type
                    ),
                )
            )
        except AudioPreprocessingError as error:
            return _RetryCandidate(
                question_code=(
                    response.question_code
                ),
                reason_code=(
                    error.reason_code.value
                ),
                required_action=(
                    "REPLACE_RESPONSE"
                ),
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

        if (
            vad_result.status
            != VadStatus.SPEECH_DETECTED
        ):
            if (
                response.question_code
                != "orientation_place"
            ):
                return _RetryCandidate(
                    question_code=(
                        response.question_code
                    ),
                    reason_code=(
                        "INCOMPLETE_ASSESSMENT"
                    ),
                    required_action=(
                        "REPLACE_RESPONSE"
                    ),
                )

            return _ProcessedQuestion(
                result=QuestionAnalysisResult(
                    question_code=(
                        response.question_code
                    ),
                    administration_status=(
                        "administered"
                    ),
                    recording_id=(
                        response.recording_id
                    ),
                    response_id=(
                        response.response_id
                    ),
                    vad_status="no_response",
                    scoring_status=(
                        "not_scored"
                    ),
                    answer_status=None,
                    wrong_event=None,
                    wrong_event_reason=None,
                    response_delay_ms=None,
                    recognized_memory_units=None,
                ),
                audio=processed_audio,
                raw_transcript=(
                    response.stt.raw_transcript
                    or ""
                ),
            )

        if response.stt.status != "success":
            return _RetryCandidate(
                question_code=(
                    response.question_code
                ),
                reason_code="UNSCORABLE_STT",
                required_action=(
                    "REPLACE_RESPONSE"
                ),
            )

        raw_transcript = (
            response.stt.raw_transcript
        )

        if raw_transcript is None:
            raise RuntimeError(
                "success STT에 전사문이 없습니다.",
            )

        question_result = (
            self._score_question(
                response=response,
                raw_transcript=raw_transcript,
                response_delay_ms=(
                    vad_result.response_delay_ms
                ),
                request=request,
            )
        )

        if isinstance(
            question_result,
            _RetryCandidate,
        ):
            return question_result

        return _ProcessedQuestion(
            result=question_result,
            audio=processed_audio,
            raw_transcript=raw_transcript,
        )

    def _score_question(
        self,
        *,
        response: AdministeredQuestionResponse,
        raw_transcript: str,
        response_delay_ms: int | None,
        request: AnalysisCreateRequest,
    ) -> (
        QuestionAnalysisResult
        | _RetryCandidate
    ):
        question_code = response.question_code

        if response_delay_ms is None:
            raise RuntimeError(
                "발화가 탐지된 문항의 "
                "응답 지연이 없습니다.",
            )

        if question_code in self._objective_codes:
            score = self._objective_service.score(
                question_code=question_code,
                raw_transcript=raw_transcript,
                assessment_local_date=(
                    request.assessment_local_date
                ),
            )

            return QuestionAnalysisResult(
                question_code=question_code,
                administration_status=(
                    "administered"
                ),
                recording_id=(
                    response.recording_id
                ),
                response_id=(
                    response.response_id
                ),
                vad_status="speech_detected",
                scoring_status=(
                    score.scoring_status
                ),
                answer_status=(
                    score.answer_status
                ),
                wrong_event=score.wrong_event,
                wrong_event_reason=(
                    score.wrong_event_reason
                ),
                response_delay_ms=(
                    response_delay_ms
                ),
                recognized_memory_units=None,
            )

        if (
            question_code
            in self._memory_failure_codes
        ):
            decision = (
                self._memory_failure_service.score(
                    question_code=question_code,
                    stt=response.stt,
                    vad_status=(
                        "speech_detected"
                    ),
                )
            )

            if isinstance(
                decision,
                MemoryFailureNeedsRetryDecision,
            ):
                return _RetryCandidate(
                    question_code=question_code,
                    reason_code=(
                        decision.reason_code
                    ),
                    required_action=(
                        "REPLACE_RESPONSE"
                    ),
                )

            if not isinstance(
                decision,
                MemoryFailureCompletedDecision,
            ):
                raise TypeError(
                    "지원하지 않는 기억 문항 "
                    "판정 결과입니다.",
                )

            if (
                question_code
                == "memory_delayed_free_recall"
                and decision.recognized_memory_units
                != request
                .recognition_plan
                .recalled_units
            ):
                return _RetryCandidate(
                    question_code=question_code,
                    reason_code=(
                        "INCOMPLETE_ASSESSMENT"
                    ),
                    required_action=(
                        "REPLACE_RESPONSE"
                    ),
                )

            return QuestionAnalysisResult(
                question_code=question_code,
                administration_status=(
                    "administered"
                ),
                recording_id=(
                    response.recording_id
                ),
                response_id=(
                    response.response_id
                ),
                vad_status="speech_detected",
                scoring_status=(
                    decision.scoring_status
                ),
                answer_status=None,
                wrong_event=(
                    decision.wrong_event
                ),
                wrong_event_reason=(
                    decision.wrong_event_reason
                ),
                response_delay_ms=(
                    response_delay_ms
                ),
                recognized_memory_units=(
                    decision
                    .recognized_memory_units
                ),
            )

        if question_code in self._excluded_codes:
            return QuestionAnalysisResult(
                question_code=question_code,
                administration_status=(
                    "administered"
                ),
                recording_id=(
                    response.recording_id
                ),
                response_id=(
                    response.response_id
                ),
                vad_status="speech_detected",
                scoring_status="not_scored",
                answer_status=None,
                wrong_event=None,
                wrong_event_reason=None,
                response_delay_ms=(
                    response_delay_ms
                ),
                recognized_memory_units=None,
            )

        raise RuntimeError(
            "문항 처리 정책이 없습니다: "
            f"{question_code}",
        )

    @staticmethod
    def _not_applicable_result(
        response: NotApplicableQuestionResponse,
    ) -> QuestionAnalysisResult:
        return QuestionAnalysisResult(
            question_code=(
                response.question_code
            ),
            administration_status=(
                "not_applicable"
            ),
            recording_id=None,
            response_id=None,
            vad_status=None,
            scoring_status=None,
            answer_status=None,
            wrong_event=None,
            wrong_event_reason=None,
            response_delay_ms=None,
            recognized_memory_units=None,
        )


def _download_retry_action(
    error: AudioDownloadError,
) -> RetryAction:
    if (
        error.reason_code
        == AudioDownloadReason.AUDIO_URL_EXPIRED
    ):
        return "REISSUE_AUDIO_URL"

    if (
        error.reason_code
        == AudioDownloadReason.AUDIO_DOWNLOAD_FAILED
        and error.retryable
    ):
        return "REISSUE_AUDIO_URL"

    return "REPLACE_RESPONSE"