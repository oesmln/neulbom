from typing import Annotated
from uuid import UUID

from fastapi import (
    APIRouter,
    Depends,
    Header,
    Request,
)
from fastapi.responses import JSONResponse

from app.api.dependencies.auth import (
    require_service_token,
)
from app.api.errors import (
    APIError,
    model_unavailable_error,
)
from app.api.schemas.analysis import (
    AnalysisAcceptedResponse,
    AnalysisCreateRequest,
    AnalysisRetryRequest,
    AnalysisStatusResponse,
)
from app.api.schemas.common import (
    IdempotencyKey,
)
from app.contracts.models import ContractBundle
from app.core.config import (
    Settings,
    get_settings,
)
from app.core.runtime import RuntimeState
from app.repositories.analysis import (
    AnalysisAlreadyExistsError,
    AnalysisRepository,
    AnalysisStatus,
    InvalidAnalysisStateError,
    SQLiteAnalysisRepository,
)
from app.repositories.idempotency import (
    SQLiteIdempotencyRepository,
    StoredHttpResponse,
)
from app.services.analysis_worker import (
    SingleAnalysisWorker,
)
from app.services.analysis_retry import (
    AnalysisRetryService,
    AnalysisRetryValidationError,
)
from app.services.assessment_completeness import (
    AssessmentCompletenessError,
    AssessmentCompletenessService,
)
from app.services.idempotency import (
    IdempotencyService,
)

router = APIRouter(
    prefix="/v1",
    tags=["Analyses"],
    dependencies=[
        Depends(require_service_token),
    ],
)


def get_analysis_repository(
    request: Request,
) -> AnalysisRepository:
    runtime_state: RuntimeState = (
        request.app.state.runtime_state
    )
    repository = (
        runtime_state.analysis_repository
    )

    if repository is None:
        raise model_unavailable_error()

    return repository


def get_analysis_worker(
    request: Request,
) -> SingleAnalysisWorker:
    runtime_state: RuntimeState = (
        request.app.state.runtime_state
    )
    worker = runtime_state.analysis_worker

    if (
        worker is None
        or not worker.is_running
    ):
        raise model_unavailable_error()

    return worker


def get_contract_bundle(
    request: Request,
) -> ContractBundle:
    runtime_state: RuntimeState = (
        request.app.state.runtime_state
    )
    contracts = runtime_state.contract_bundle

    if contracts is None:
        raise model_unavailable_error()

    return contracts


def get_completeness_service(
    contracts: Annotated[
        ContractBundle,
        Depends(get_contract_bundle),
    ],
) -> AssessmentCompletenessService:
    return (
        AssessmentCompletenessService
        .from_contract_bundle(contracts)
    )

def get_analysis_retry_service(
    contracts: Annotated[
        ContractBundle,
        Depends(get_contract_bundle),
    ],
) -> AnalysisRetryService:
    return (
        AnalysisRetryService
        .from_contract_bundle(contracts)
    )

def get_analysis_idempotency_service(
    settings: Annotated[
        Settings,
        Depends(get_settings),
    ],
) -> IdempotencyService:
    repository = SQLiteIdempotencyRepository(
        settings.idempotency_db_path,
    )

    return IdempotencyService(
        repository,
    )


@router.post(
    "/analyses",
    response_model=AnalysisAcceptedResponse,
    status_code=202,
    operation_id="createAnalysis",
)
async def create_analysis(
    request_body: AnalysisCreateRequest,
    idempotency_key: Annotated[
        IdempotencyKey,
        Header(alias="Idempotency-Key"),
    ],
    repository: Annotated[
        AnalysisRepository,
        Depends(get_analysis_repository),
    ],
    worker: Annotated[
        SingleAnalysisWorker,
        Depends(get_analysis_worker),
    ],
    completeness_service: Annotated[
        AssessmentCompletenessService,
        Depends(get_completeness_service),
    ],
    idempotency_service: Annotated[
        IdempotencyService,
        Depends(
            get_analysis_idempotency_service,
        ),
    ],
) -> JSONResponse:
    try:
        completeness_service.validate(
            request_body,
        )
    except AssessmentCompletenessError as error:
        raise APIError(
            status_code=422,
            code="VALIDATION_ERROR",
            message=(
                "Assessment question composition "
                "is incomplete or inconsistent."
            ),
            retryable=False,
            details={
                "fields": [
                    "responses",
                    "recognition_plan",
                ],
            },
        ) from error

    async def operation() -> StoredHttpResponse:
        try:
            stored = repository.create_pending(
                analysis_id=(
                    request_body.analysis_id
                ),
                assessment_id=(
                    request_body.assessment_id
                ),
                request_body=(
                    request_body.model_dump(
                        mode="json",
                    )
                ),
            )
        except AnalysisAlreadyExistsError as error:
            raise APIError(
                status_code=409,
                code="INVALID_ANALYSIS_STATE",
                message=(
                    "The analysis_id already exists."
                ),
                retryable=False,
                details={
                    "analysis_id": str(
                        request_body.analysis_id,
                    ),
                },
            ) from error

        await worker.enqueue(
            stored.analysis_id,
        )

        accepted = AnalysisAcceptedResponse(
            analysis_id=stored.analysis_id,
            assessment_id=stored.assessment_id,
            status="pending",
            created_at=stored.created_at,
        )

        return StoredHttpResponse(
            status_code=202,
            body=accepted.model_dump(
                mode="json",
            ),
            headers={
                "Location": (
                    "/v1/analyses/"
                    f"{stored.analysis_id}"
                ),
                "Retry-After": "2",
            },
        )

    execution = (
        await idempotency_service.execute_async(
            scope=(
                "analysis-create:"
                f"{request_body.analysis_id}"
            ),
            idempotency_key=idempotency_key,
            request_body=request_body.model_dump(
                mode="json",
            ),
            operation=operation,
        )
    )

    return JSONResponse(
        status_code=(
            execution.response.status_code
        ),
        content=execution.response.body,
        headers=execution.response.headers,
    )

@router.post(
    "/analyses/{analysis_id}/retry",
    response_model=AnalysisAcceptedResponse,
    status_code=202,
    operation_id="retryAnalysis",
)
async def retry_analysis(
    analysis_id: UUID,
    request_body: AnalysisRetryRequest,
    idempotency_key: Annotated[
        IdempotencyKey,
        Header(alias="Idempotency-Key"),
    ],
    repository: Annotated[
        AnalysisRepository,
        Depends(get_analysis_repository),
    ],
    worker: Annotated[
        SingleAnalysisWorker,
        Depends(get_analysis_worker),
    ],
    retry_service: Annotated[
        AnalysisRetryService,
        Depends(get_analysis_retry_service),
    ],
    idempotency_service: Annotated[
        IdempotencyService,
        Depends(
            get_analysis_idempotency_service,
        ),
    ],
) -> JSONResponse:
    async def operation() -> StoredHttpResponse:
        stored = repository.get(
            analysis_id,
        )

        if stored is None:
            raise APIError(
                status_code=404,
                code="ANALYSIS_NOT_FOUND",
                message=(
                    "The requested analysis "
                    "does not exist."
                ),
                retryable=False,
                details={
                    "analysis_id": str(
                        analysis_id,
                    ),
                },
            )

        if (
            stored.status
            != AnalysisStatus.NEEDS_RETRY
            or not stored.retryable
        ):
            raise APIError(
                status_code=409,
                code="INVALID_ANALYSIS_STATE",
                message=(
                    "Only an analysis in the "
                    "needs_retry state can be resumed."
                ),
                retryable=False,
                details={
                    "analysis_id": str(
                        analysis_id,
                    ),
                    "status": (
                        stored.status.value
                    ),
                },
            )

        try:
            updated_request = (
                retry_service.merge_request(
                    analysis=stored,
                    retry_request=request_body,
                )
            )
        except (
            AnalysisRetryValidationError
        ) as error:
            raise APIError(
                status_code=422,
                code="VALIDATION_ERROR",
                message=(
                    "The retry request does not "
                    "match the required retry items."
                ),
                retryable=False,
                details={
                    "fields": [
                        "reason_code",
                        "items",
                    ],
                },
            ) from error

        try:
            resumed = (
                repository.resume_with_request(
                    analysis_id=analysis_id,
                    updated_request_body=(
                        updated_request.model_dump(
                            mode="json",
                        )
                    ),
                )
            )
        except InvalidAnalysisStateError as error:
            raise APIError(
                status_code=409,
                code="INVALID_ANALYSIS_STATE",
                message=(
                    "The analysis state changed "
                    "before the retry was applied."
                ),
                retryable=True,
                details={
                    "analysis_id": str(
                        analysis_id,
                    ),
                },
            ) from error

        await worker.enqueue(
            analysis_id,
        )

        accepted = AnalysisAcceptedResponse(
            analysis_id=resumed.analysis_id,
            assessment_id=(
                resumed.assessment_id
            ),
            status="pending",
            created_at=resumed.created_at,
        )

        return StoredHttpResponse(
            status_code=202,
            body=accepted.model_dump(
                mode="json",
            ),
            headers={
                "Location": (
                    "/v1/analyses/"
                    f"{analysis_id}"
                ),
                "Retry-After": "2",
            },
        )

    execution = (
        await idempotency_service.execute_async(
            scope=(
                "analysis-retry:"
                f"{analysis_id}"
            ),
            idempotency_key=idempotency_key,
            request_body=(
                request_body.model_dump(
                    mode="json",
                )
            ),
            operation=operation,
        )
    )

    return JSONResponse(
        status_code=(
            execution.response.status_code
        ),
        content=execution.response.body,
        headers=execution.response.headers,
    )

@router.get(
    "/analyses/{analysis_id}",
    response_model=AnalysisStatusResponse,
    status_code=200,
    operation_id="getAnalysis",
)
def get_analysis(
    analysis_id: UUID,
    repository: Annotated[
        AnalysisRepository,
        Depends(get_analysis_repository),
    ],
) -> JSONResponse:
    stored = repository.get(
        analysis_id,
    )

    if stored is None:
        raise APIError(
            status_code=404,
            code="ANALYSIS_NOT_FOUND",
            message=(
                "The requested analysis "
                "does not exist."
            ),
            retryable=False,
            details={
                "analysis_id": str(
                    analysis_id,
                ),
            },
        )

    response = AnalysisStatusResponse(
        analysis_id=stored.analysis_id,
        assessment_id=stored.assessment_id,
        status=stored.status.value,
        created_at=stored.created_at,
        updated_at=stored.updated_at,
        retryable=stored.retryable,
        reason_code=stored.reason_code,
        retry_items=list(
            stored.retry_items,
        ),
        result=stored.result_body,
    )

    headers: dict[str, str] = {}

    if stored.status.value in {
        "pending",
        "processing",
    }:
        headers["Retry-After"] = "2"

    return JSONResponse(
        status_code=200,
        content=response.model_dump(
            mode="json",
        ),
        headers=headers,
    )