from collections.abc import AsyncIterator
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
from app.api.errors import model_unavailable_error
from app.api.schemas.common import IdempotencyKey
from app.api.schemas.recognition_plan import (
    RecognitionPlanRequest,
    RecognitionPlanResponse,
)
from app.audio.downloader import (
    SignedAudioDownloader,
    create_audio_http_client,
)
from app.audio.vad import (
    VadService,
    get_vad_service,
)
from app.contracts.models import ContractBundle
from app.core.config import Settings, get_settings
from app.core.runtime import RuntimeState
from app.repositories.idempotency import (
    SQLiteIdempotencyRepository,
    StoredHttpResponse,
)
from app.services.idempotency import (
    IdempotencyService,
)
from app.services.recognition_plan_workflow import (
    RecognitionPlanWorkflow,
)

router = APIRouter(
    prefix="/v1",
    tags=["Recognition Plan"],
    dependencies=[
        Depends(require_service_token),
    ],
)


def get_contract_bundle(
    request: Request,
) -> ContractBundle:
    runtime_state: RuntimeState = (
        request.app.state.runtime_state
    )

    if runtime_state.contract_bundle is None:
        raise model_unavailable_error()

    return runtime_state.contract_bundle


def get_idempotency_service(
    settings: Annotated[
        Settings,
        Depends(get_settings),
    ],
) -> IdempotencyService:
    repository = SQLiteIdempotencyRepository(
        settings.idempotency_db_path,
    )
    return IdempotencyService(repository)


async def get_audio_downloader(
    settings: Annotated[
        Settings,
        Depends(get_settings),
    ],
) -> AsyncIterator[SignedAudioDownloader]:
    client = create_audio_http_client(
        timeout_seconds=(
            settings.audio_download_timeout_seconds
        ),
    )

    try:
        yield SignedAudioDownloader(
            client=client,
            max_size_bytes=(
                settings.max_audio_download_bytes
            ),
        )
    finally:
        await client.aclose()


def get_vad_dependency() -> VadService:
    try:
        return get_vad_service()
    except Exception as error:
        raise model_unavailable_error() from error


def get_recognition_plan_workflow(
    contract_bundle: Annotated[
        ContractBundle,
        Depends(get_contract_bundle),
    ],
    audio_downloader: Annotated[
        SignedAudioDownloader,
        Depends(get_audio_downloader),
    ],
    vad_service: Annotated[
        VadService,
        Depends(get_vad_dependency),
    ],
) -> RecognitionPlanWorkflow:
    return RecognitionPlanWorkflow(
        contract_bundle=contract_bundle,
        audio_downloader=audio_downloader,
        vad_service=vad_service,
    )


@router.post(
    "/assessments/{assessment_id}/recognition-plan",
    response_model=RecognitionPlanResponse,
    status_code=200,
    operation_id="createRecognitionPlan",
)
async def create_recognition_plan(
    assessment_id: UUID,
    request_body: RecognitionPlanRequest,
    idempotency_key: Annotated[
        IdempotencyKey,
        Header(alias="Idempotency-Key"),
    ],
    workflow: Annotated[
        RecognitionPlanWorkflow,
        Depends(get_recognition_plan_workflow),
    ],
    idempotency_service: Annotated[
        IdempotencyService,
        Depends(get_idempotency_service),
    ],
) -> JSONResponse:
    async def operation() -> StoredHttpResponse:
        response = await workflow.execute(
            assessment_id=assessment_id,
            request=request_body,
        )

        return StoredHttpResponse(
            status_code=200,
            body=response.model_dump(
                mode="json",
            ),
            headers={},
        )

    execution = (
        await idempotency_service.execute_async(
            scope=(
                f"recognition-plan:{assessment_id}"
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