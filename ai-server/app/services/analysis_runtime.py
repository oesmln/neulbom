import asyncio
from collections.abc import Callable
from pathlib import Path

from fastapi.concurrency import (
    run_in_threadpool,
)

from app.audio.downloader import (
    SignedAudioDownloader,
)
from app.audio.vad import (
    VadConfigurationError,
    get_vad_service,
)
from app.contracts.models import ContractBundle
from app.inference.artifacts import (
    ArtifactValidationError,
    discover_model_artifacts,
)
from app.inference.ast import (
    AstInferenceError,
    AstInferenceService,
)
from app.inference.fusion import (
    FusionInferenceError,
    FusionInferenceService,
)
from app.inference.kcelectra import (
    KcElectraInferenceError,
    KcElectraInferenceService,
)
from app.repositories.analysis import (
    StoredAnalysis,
)
from app.services.analysis_worker import (
    AnalysisModelUnavailableError,
    AnalysisProcessingOutcome,
    AnalysisProcessor,
)
from app.services.session_analysis import (
    SessionAnalysisProcessor,
)

AnalysisProcessorFactory = Callable[
    [],
    AnalysisProcessor,
]


class LazySessionAnalysisProcessor:
    """
    첫 분석 요청에서 모델을 한 번만 로딩한다.

    이후 작업은 같은 AST, KcELECTRA, fusion,
    VAD 인스턴스를 재사용한다.
    """

    def __init__(
        self,
        *,
        contracts: ContractBundle,
        audio_downloader: SignedAudioDownloader,
        artifacts_dir: Path,
        processor_factory: (
            AnalysisProcessorFactory | None
        ) = None,
    ) -> None:
        self._contracts = contracts
        self._audio_downloader = (
            audio_downloader
        )
        self._artifacts_dir = (
            artifacts_dir.resolve()
        )
        self._processor: (
            AnalysisProcessor | None
        ) = None
        self._load_lock = asyncio.Lock()
        self._processor_factory = (
            processor_factory
            or self._build_default_processor
        )

    @property
    def is_loaded(self) -> bool:
        return self._processor is not None

    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisProcessingOutcome:
        processor = await self._get_processor()

        return await processor.process(
            analysis,
        )

    async def _get_processor(
        self,
    ) -> AnalysisProcessor:
        if self._processor is not None:
            return self._processor

        async with self._load_lock:
            if self._processor is not None:
                return self._processor

            try:
                processor = await run_in_threadpool(
                    self._processor_factory,
                )
            except (
                ArtifactValidationError,
                AstInferenceError,
                KcElectraInferenceError,
                FusionInferenceError,
                VadConfigurationError,
                OSError,
            ) as error:
                raise AnalysisModelUnavailableError(
                    "분석 모델을 로딩하지 "
                    "못했습니다.",
                ) from error

            if not callable(
                getattr(
                    processor,
                    "process",
                    None,
                ),
            ):
                raise AnalysisModelUnavailableError(
                    "분석 처리기 형식이 "
                    "올바르지 않습니다.",
                )

            self._processor = processor

        return self._processor

    def _build_default_processor(
        self,
    ) -> SessionAnalysisProcessor:
        artifacts = discover_model_artifacts(
            self._artifacts_dir,
        )

        vad_service = get_vad_service()

        ast_service = (
            AstInferenceService.from_artifacts(
                artifacts=artifacts,
                contracts=self._contracts,
            )
        )
        kcelectra_service = (
            KcElectraInferenceService
            .from_artifacts(
                artifacts=artifacts,
                contracts=self._contracts,
            )
        )
        fusion_service = (
            FusionInferenceService
            .from_artifacts(
                artifacts,
            )
        )

        return SessionAnalysisProcessor(
            contracts=self._contracts,
            audio_downloader=(
                self._audio_downloader
            ),
            vad_service=vad_service,
            ast_service=ast_service,
            kcelectra_service=(
                kcelectra_service
            ),
            fusion_service=fusion_service,
        )