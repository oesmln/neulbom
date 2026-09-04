import asyncio
import logging
from dataclasses import dataclass
from typing import Any, Protocol
from uuid import UUID

from app.repositories.analysis import (
    AnalysisRepository,
    AnalysisStatus,
    InvalidAnalysisStateError,
    StoredAnalysis,
)

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class AnalysisCompleted:
    result_body: dict[str, Any]


@dataclass(frozen=True, slots=True)
class AnalysisNeedsRetry:
    reason_code: str
    retry_items: tuple[
        dict[str, Any],
        ...,
    ]


AnalysisProcessingOutcome = (
    AnalysisCompleted
    | AnalysisNeedsRetry
)


class AnalysisProcessor(Protocol):
    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisProcessingOutcome:
        """저장된 분석 요청을 실제로 처리한다."""
        ...


class AnalysisModelUnavailableError(
    RuntimeError,
):
    """분석에 필요한 모델을 사용할 수 없는 경우."""


class AnalysisWorkerNotStartedError(
    RuntimeError,
):
    """시작하지 않은 워커에 작업을 추가한 경우."""


class AnalysisAlreadyQueuedError(
    RuntimeError,
):
    """동일한 분석 작업이 이미 대기 중인 경우."""


class SingleAnalysisWorker:
    """
    분석 작업을 한 번에 하나씩 처리한다.

    AST와 KcELECTRA가 GPU를 사용하는 경우에도
    하나의 소비자 task만 실행하므로 모델 추론이
    동시에 실행되지 않는다.
    """

    def __init__(
        self,
        *,
        repository: AnalysisRepository,
        processor: AnalysisProcessor,
        processing_timeout_seconds: float = 300.0,
    ) -> None:
        if processing_timeout_seconds <= 0:
            raise ValueError(
                "분석 처리 제한 시간은 "
                "0초보다 커야 합니다.",
            )

        self._repository = repository
        self._processor = processor
        self._processing_timeout_seconds = (
            processing_timeout_seconds
        )
        self._queue: asyncio.Queue[
            UUID | None
        ] = asyncio.Queue()
        self._queued_analysis_ids: set[
            UUID
        ] = set()
        self._task: asyncio.Task[None] | None = (
            None
        )

    @property
    def is_running(self) -> bool:
        return (
            self._task is not None
            and not self._task.done()
        )

    @property
    def processing_timeout_seconds(
        self,
    ) -> float:
        return (
            self._processing_timeout_seconds
        )

    async def start(self) -> None:
        if self.is_running:
            return

        self._task = asyncio.create_task(
            self._run(),
            name="single-analysis-worker",
        )

    async def stop(self) -> None:
        task = self._task

        if task is None:
            return

        if not task.done():
            await self._queue.put(None)
            await task

        self._task = None
        self._queued_analysis_ids.clear()

    async def enqueue(
        self,
        analysis_id: UUID,
    ) -> None:
        if not self.is_running:
            raise AnalysisWorkerNotStartedError(
                "분석 워커가 시작되지 않았습니다.",
            )

        if (
            analysis_id
            in self._queued_analysis_ids
        ):
            raise AnalysisAlreadyQueuedError(
                "동일한 분석 작업이 이미 "
                "대기 중입니다.",
            )

        analysis = self._repository.get(
            analysis_id,
        )

        if analysis is None:
            raise ValueError(
                "대기열에 추가할 분석 작업을 "
                "찾을 수 없습니다.",
            )

        if (
            analysis.status
            != AnalysisStatus.PENDING
        ):
            raise InvalidAnalysisStateError(
                "pending 상태의 분석 작업만 "
                "대기열에 추가할 수 있습니다: "
                f"status={analysis.status.value}",
            )

        self._queued_analysis_ids.add(
            analysis_id,
        )
        await self._queue.put(
            analysis_id,
        )

    async def join(self) -> None:
        """현재 대기 중인 작업이 모두 끝날 때까지 기다린다."""
        await self._queue.join()

    async def _run(self) -> None:
        while True:
            analysis_id = await self._queue.get()

            try:
                if analysis_id is None:
                    return

                await self._process_one(
                    analysis_id,
                )
            finally:
                if analysis_id is not None:
                    self._queued_analysis_ids.discard(
                        analysis_id,
                    )

                self._queue.task_done()

    async def _process_one(
        self,
        analysis_id: UUID,
    ) -> None:
        try:
            analysis = (
                self._repository.mark_processing(
                    analysis_id,
                )
            )

            outcome = await asyncio.wait_for(
                self._processor.process(
                    analysis,
                ),
                timeout=(
                    self._processing_timeout_seconds
                ),
            )

            if isinstance(
                outcome,
                AnalysisCompleted,
            ):
                self._repository.mark_completed(
                    analysis_id=analysis_id,
                    result_body=(
                        outcome.result_body
                    ),
                )
                return

            if isinstance(
                outcome,
                AnalysisNeedsRetry,
            ):
                self._repository.mark_needs_retry(
                    analysis_id=analysis_id,
                    reason_code=(
                        outcome.reason_code
                    ),
                    retry_items=(
                        outcome.retry_items
                    ),
                )
                return

            raise TypeError(
                "지원하지 않는 분석 처리 결과입니다.",
            )

        except TimeoutError:
            logger.error(
                "Analysis processing timed out",
                extra={
                    "analysis_id": str(
                        analysis_id,
                    ),
                    "timeout_seconds": (
                        self
                        ._processing_timeout_seconds
                    ),
                },
            )
            self._mark_failed_safely(
                analysis_id=analysis_id,
                reason_code="INTERNAL_ERROR",
            )

        except AnalysisModelUnavailableError:
            logger.exception(
                "Analysis model unavailable",
                extra={
                    "analysis_id": str(
                        analysis_id,
                    ),
                },
            )
            self._mark_failed_safely(
                analysis_id=analysis_id,
                reason_code="MODEL_UNAVAILABLE",
            )

        except InvalidAnalysisStateError:
            logger.warning(
                "Analysis state transition rejected",
                extra={
                    "analysis_id": str(
                        analysis_id,
                    ),
                },
                exc_info=True,
            )

        except Exception:
            # 요청 본문, 전사문, signed URL은
            # 로그에 포함하지 않는다.
            logger.exception(
                "Analysis processing failed",
                extra={
                    "analysis_id": str(
                        analysis_id,
                    ),
                },
            )
            self._mark_failed_safely(
                analysis_id=analysis_id,
                reason_code="INTERNAL_ERROR",
            )

    def _mark_failed_safely(
        self,
        *,
        analysis_id: UUID,
        reason_code: str,
    ) -> None:
        try:
            analysis = self._repository.get(
                analysis_id,
            )

            if (
                analysis is None
                or analysis.status
                != AnalysisStatus.PROCESSING
            ):
                return

            self._repository.mark_failed(
                analysis_id=analysis_id,
                reason_code=reason_code,
            )
        except Exception:
            logger.exception(
                "Failed to persist analysis failure",
                extra={
                    "analysis_id": str(
                        analysis_id,
                    ),
                },
            )