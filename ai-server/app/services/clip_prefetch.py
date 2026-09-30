"""Non-blocking, best-effort CIST clip prefetch."""

import asyncio
import hashlib
import logging

from app.api.schemas.analysis import ClipPrefetchRequest
from app.services.analysis_runtime import LazySessionAnalysisProcessor

logger = logging.getLogger(__name__)


class ClipPrefetchWorker:
    def __init__(self, processor: LazySessionAnalysisProcessor) -> None:
        self._processor = processor
        self._queue: asyncio.Queue[tuple[str, ClipPrefetchRequest]] = asyncio.Queue()
        self._queued: set[str] = set()
        self._task: asyncio.Task[None] | None = None

    @property
    def is_running(self) -> bool:
        return self._task is not None and not self._task.done()

    async def start(self) -> None:
        if not self.is_running:
            self._task = asyncio.create_task(self._run(), name="clip-prefetch-worker")

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            self._task = None
        self._queued.clear()

    async def enqueue(self, request: ClipPrefetchRequest) -> bool:
        if not self.is_running:
            raise RuntimeError("클립 선분석 워커가 실행 중이지 않습니다.")
        response = request.response
        identity = (
            f"{response.recording_id}:{response.response_id}:"
            f"{response.question_code}:{response.stt.raw_transcript}:"
            f"{request.question_set_version}"
        )
        key = hashlib.sha256(identity.encode("utf-8")).hexdigest()
        if key in self._queued:
            return False
        self._queued.add(key)
        await self._queue.put((key, request))
        return True

    async def _run(self) -> None:
        while True:
            key, request = await self._queue.get()
            try:
                await self._processor.prefetch_clip(request)
            except Exception as error:
                logger.warning(
                    "Clip prefetch failed",
                    extra={
                        "recording_id": str(request.response.recording_id),
                        "question_code": request.response.question_code,
                        "error_type": type(error).__name__,
                    },
                )
            finally:
                self._queued.discard(key)
                self._queue.task_done()
