from pydantic import ValidationError

from app.api.schemas.analysis import (
    AdministeredQuestionResponse,
    AnalysisCreateRequest,
    AnalysisRetryRequest,
    ReissueAudioUrlItem,
    ReplaceResponseItem,
    RetryItem,
)
from app.contracts.models import ContractBundle
from app.repositories.analysis import (
    AnalysisStatus,
    StoredAnalysis,
)
from app.services.assessment_completeness import (
    AssessmentCompletenessError,
    AssessmentCompletenessService,
)


class AnalysisRetryValidationError(ValueError):
    """분석 재시도 요청이 저장된 상태와 일치하지 않는 경우."""


class AnalysisRetryService:
    def __init__(
        self,
        *,
        completeness_service: AssessmentCompletenessService,
    ) -> None:
        self._completeness_service = completeness_service

    @classmethod
    def from_contract_bundle(
        cls,
        bundle: ContractBundle,
    ) -> "AnalysisRetryService":
        return cls(
            completeness_service=(
                AssessmentCompletenessService
                .from_contract_bundle(bundle)
            ),
        )

    def merge_request(
        self,
        *,
        analysis: StoredAnalysis,
        retry_request: AnalysisRetryRequest,
    ) -> AnalysisCreateRequest:
        self._validate_analysis_state(
            analysis,
        )

        original_request = (
            self._load_original_request(
                analysis,
            )
        )

        expected_items = (
            self._load_expected_retry_items(
                analysis,
            )
        )

        self._validate_retry_request(
            analysis=analysis,
            retry_request=retry_request,
            expected_items=expected_items,
        )

        responses_by_code = {
            response.question_code: response
            for response
            in original_request.responses
        }

        updated_responses = dict(
            responses_by_code,
        )

        for retry_item in retry_request.items:
            original_response = (
                responses_by_code[
                    retry_item.question_code
                ]
            )

            if not isinstance(
                original_response,
                AdministeredQuestionResponse,
            ):
                raise AnalysisRetryValidationError(
                    "미시행 문항은 재시도할 수 "
                    "없습니다: "
                    f"{retry_item.question_code}",
                )

            if isinstance(
                retry_item,
                ReissueAudioUrlItem,
            ):
                updated_responses[
                    retry_item.question_code
                ] = self._merge_reissued_audio(
                    original_response=(
                        original_response
                    ),
                    retry_item=retry_item,
                )
                continue

            if isinstance(
                retry_item,
                ReplaceResponseItem,
            ):
                updated_responses[
                    retry_item.question_code
                ] = self._replace_response(
                    original_response=(
                        original_response
                    ),
                    retry_item=retry_item,
                )
                continue

            raise AnalysisRetryValidationError(
                "지원하지 않는 재시도 작업입니다.",
            )

        updated_payload = (
            original_request.model_dump(
                mode="json",
            )
        )
        updated_payload["responses"] = [
            updated_responses[
                response.question_code
            ].model_dump(
                mode="json",
            )
            for response
            in original_request.responses
        ]

        try:
            updated_request = (
                AnalysisCreateRequest
                .model_validate(
                    updated_payload,
                )
            )
            self._completeness_service.validate(
                updated_request,
            )
        except (
            ValidationError,
            AssessmentCompletenessError,
        ) as error:
            raise AnalysisRetryValidationError(
                "재시도 요청을 병합한 결과가 "
                "17개 문항 완결성 정책과 "
                "일치하지 않습니다.",
            ) from error

        return updated_request

    @staticmethod
    def _validate_analysis_state(
        analysis: StoredAnalysis,
    ) -> None:
        if (
            analysis.status
            != AnalysisStatus.NEEDS_RETRY
        ):
            raise AnalysisRetryValidationError(
                "needs_retry 상태의 분석만 "
                "재시도할 수 있습니다: "
                f"status={analysis.status.value}",
            )

        if (
            not analysis.retryable
            or analysis.reason_code is None
            or not analysis.retry_items
            or analysis.result_body is not None
        ):
            raise AnalysisRetryValidationError(
                "저장된 분석의 재시도 상태가 "
                "올바르지 않습니다.",
            )

    @staticmethod
    def _load_original_request(
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
            raise AnalysisRetryValidationError(
                "저장된 원본 분석 요청이 "
                "올바르지 않습니다.",
            ) from error

        if (
            request.analysis_id
            != analysis.analysis_id
        ):
            raise AnalysisRetryValidationError(
                "저장된 요청의 analysis_id가 "
                "분석 작업과 일치하지 않습니다.",
            )

        if (
            request.assessment_id
            != analysis.assessment_id
        ):
            raise AnalysisRetryValidationError(
                "저장된 요청의 assessment_id가 "
                "분석 작업과 일치하지 않습니다.",
            )

        return request

    @staticmethod
    def _load_expected_retry_items(
        analysis: StoredAnalysis,
    ) -> dict[str, RetryItem]:
        try:
            retry_items = [
                RetryItem.model_validate(item)
                for item in analysis.retry_items
            ]
        except ValidationError as error:
            raise AnalysisRetryValidationError(
                "저장된 재시도 항목이 "
                "올바르지 않습니다.",
            ) from error

        retry_items_by_code = {
            item.question_code: item
            for item in retry_items
        }

        if len(retry_items_by_code) != len(
            retry_items,
        ):
            raise AnalysisRetryValidationError(
                "저장된 재시도 문항 코드가 "
                "중복되었습니다.",
            )

        return retry_items_by_code

    @staticmethod
    def _validate_retry_request(
        *,
        analysis: StoredAnalysis,
        retry_request: AnalysisRetryRequest,
        expected_items: dict[str, RetryItem],
    ) -> None:
        if (
            retry_request.reason_code
            != analysis.reason_code
        ):
            raise AnalysisRetryValidationError(
                "재시도 사유 코드가 저장된 "
                "분석 상태와 일치하지 않습니다.",
            )

        requested_codes = {
            item.question_code
            for item in retry_request.items
        }
        expected_codes = set(
            expected_items,
        )

        if requested_codes != expected_codes:
            missing_codes = sorted(
                expected_codes - requested_codes,
            )
            unexpected_codes = sorted(
                requested_codes - expected_codes,
            )

            raise AnalysisRetryValidationError(
                "재시도 문항 구성이 저장된 "
                "재시도 항목과 일치하지 않습니다: "
                f"missing={missing_codes}, "
                f"unexpected={unexpected_codes}",
            )

        for item in retry_request.items:
            expected_action = (
                expected_items[
                    item.question_code
                ].required_action
            )

            if (
                item.retry_action
                != expected_action
            ):
                raise AnalysisRetryValidationError(
                    "재시도 작업이 서버가 요청한 "
                    "작업과 일치하지 않습니다: "
                    f"question_code="
                    f"{item.question_code}, "
                    f"expected={expected_action}, "
                    f"actual={item.retry_action}",
                )

    @staticmethod
    def _merge_reissued_audio(
        *,
        original_response: AdministeredQuestionResponse,
        retry_item: ReissueAudioUrlItem,
    ) -> AdministeredQuestionResponse:
        if (
            retry_item.recording_id
            != original_response.recording_id
        ):
            raise AnalysisRetryValidationError(
                "URL만 재발급하는 경우 "
                "recording_id를 변경할 수 "
                "없습니다.",
            )

        if (
            retry_item.response_id
            != original_response.response_id
        ):
            raise AnalysisRetryValidationError(
                "URL만 재발급하는 경우 "
                "response_id를 변경할 수 "
                "없습니다.",
            )

        payload = original_response.model_dump(
            mode="json",
        )
        payload["audio"] = (
            retry_item.audio.model_dump(
                mode="json",
            )
        )

        return (
            AdministeredQuestionResponse
            .model_validate(
                payload,
            )
        )

    @staticmethod
    def _replace_response(
        *,
        original_response: AdministeredQuestionResponse,
        retry_item: ReplaceResponseItem,
    ) -> AdministeredQuestionResponse:
        if (
            retry_item.variant_id
            != original_response.variant_id
        ):
            raise AnalysisRetryValidationError(
                "교체 응답의 variant_id가 "
                "원본 문항과 일치하지 않습니다.",
            )

        if (
            retry_item.recording_id
            == original_response.recording_id
        ):
            raise AnalysisRetryValidationError(
                "응답을 교체하는 경우 새로운 "
                "recording_id가 필요합니다.",
            )

        if (
            retry_item.response_id
            == original_response.response_id
        ):
            raise AnalysisRetryValidationError(
                "응답을 교체하는 경우 새로운 "
                "response_id가 필요합니다.",
            )

        return AdministeredQuestionResponse(
            question_code=(
                retry_item.question_code
            ),
            variant_id=retry_item.variant_id,
            administration_status=(
                "administered"
            ),
            recording_id=(
                retry_item.recording_id
            ),
            response_id=retry_item.response_id,
            audio=retry_item.audio,
            stt=retry_item.stt,
            timing=retry_item.timing,
        )