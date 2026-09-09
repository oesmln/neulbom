import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass

from fastapi import FastAPI

from app.audio.downloader import (
    SignedAudioDownloader,
    create_audio_http_client,
)
from app.contracts.loader import (
    ContractLoadError,
    load_contract_bundle,
)
from app.contracts.models import ContractBundle
from app.contracts.validator import (
    ContractValidationError,
)
from app.core.config import get_settings
from app.inference.artifacts import (
    ArtifactValidationError,
    discover_model_artifacts,
)
from app.repositories.analysis import (
    SQLiteAnalysisRepository,
)
from app.services.analysis_runtime import (
    LazySessionAnalysisProcessor,
)
from app.services.analysis_worker import (
    SingleAnalysisWorker,
)

logger = logging.getLogger(__name__)


@dataclass(slots=True)
class RuntimeState:
    """AI 서버 실행 중 공유하는 준비 상태."""

    contract_bundle: ContractBundle | None = None
    contract_error: str | None = None
    artifact_error: str | None = None
    analysis_repository: (
        SQLiteAnalysisRepository | None
    ) = None
    analysis_worker: (
        SingleAnalysisWorker | None
    ) = None
    analysis_runtime_error: str | None = None

    @property
    def is_ready(self) -> bool:
        return (
            self.contract_bundle is not None
            and self.contract_error is None
            and self.artifact_error is None
            and self.analysis_repository
            is not None
            and self.analysis_worker is not None
            and self.analysis_worker.is_running
            and self.analysis_runtime_error is None
        )


@asynccontextmanager
async def lifespan(
    app: FastAPI,
) -> AsyncIterator[None]:
    """서버 시작과 종료 시 필요한 자원을 관리한다."""
    runtime_state = RuntimeState()
    app.state.runtime_state = runtime_state

    settings = get_settings()
    audio_client = None
    worker: SingleAnalysisWorker | None = None

    try:
        runtime_state.contract_bundle = (
            load_contract_bundle(
                settings.contracts_dir,
            )
        )
    except (
        ContractLoadError,
        ContractValidationError,
    ) as error:
        runtime_state.contract_error = str(
            error,
        )
        logger.exception(
            "기준 계약 파일을 로딩하지 못했습니다.",
        )

    if runtime_state.contract_bundle is not None:
        try:
            discover_model_artifacts(
                settings.artifacts_dir,
            )
        except (
            ArtifactValidationError,
            OSError,
        ) as error:
            runtime_state.artifact_error = str(
                error,
            )
            logger.exception(
                "모델 아티팩트 사전 검증에 "
                "실패했습니다.",
            )

    if (
        runtime_state.contract_bundle is not None
        and runtime_state.artifact_error is None
    ):
        try:
            audio_client = (
                create_audio_http_client(
                    timeout_seconds=(
                        settings
                        .audio_download_timeout_seconds
                    ),
                )
            )
            audio_downloader = (
                SignedAudioDownloader(
                    client=audio_client,
                    max_size_bytes=(
                        settings
                        .max_audio_download_bytes
                    ),
                    allowed_hosts=(
                        settings
                        .allowed_audio_download_hosts
                    ),
                )
            )
            repository = (
                SQLiteAnalysisRepository(
                    settings.analysis_db_path,
                )
            )
            recovered_analysis_ids = (
                repository.recover_incomplete()
            )
            processor = (
                LazySessionAnalysisProcessor(
                    contracts=(
                        runtime_state
                        .contract_bundle
                    ),
                    audio_downloader=(
                        audio_downloader
                    ),
                    artifacts_dir=(
                        settings.artifacts_dir
                    ),
                )
            )
            worker = SingleAnalysisWorker(
                repository=repository,
                processor=processor,
                processing_timeout_seconds=(
                    settings
                    .analysis_processing_timeout_seconds
                ),
            )

            await worker.start()

            for analysis_id in (
                recovered_analysis_ids
            ):
                await worker.enqueue(
                    analysis_id,
                )

            if recovered_analysis_ids:
                logger.info(
                    "Recovered incomplete analyses",
                    extra={
                        "recovered_analysis_count": (
                            len(
                                recovered_analysis_ids,
                            )
                        ),
                    },
                )

            runtime_state.analysis_repository = (
                repository
            )
            runtime_state.analysis_worker = (
                worker
            )
        except Exception as error:
            runtime_state.analysis_runtime_error = (
                str(error)
            )
            logger.exception(
                "비동기 분석 런타임을 "
                "초기화하지 못했습니다.",
            )

    try:
        yield
    finally:
        if worker is not None:
            await worker.stop()

        if audio_client is not None:
            await audio_client.aclose()

        runtime_state.analysis_worker = None
        runtime_state.analysis_repository = None
        runtime_state.contract_bundle = None
