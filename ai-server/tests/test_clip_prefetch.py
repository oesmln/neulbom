import asyncio
from types import SimpleNamespace
from uuid import uuid4

from app.services.clip_prefetch import ClipPrefetchWorker


def _request():
    return SimpleNamespace(
        question_set_version="cist-v1",
        response=SimpleNamespace(
            recording_id=uuid4(),
            response_id=uuid4(),
            question_code="orientation_year",
            stt=SimpleNamespace(raw_transcript="2026년"),
        ),
    )


def test_duplicate_enqueue_and_failed_clip_do_not_stop_next_clip() -> None:
    class Processor:
        def __init__(self) -> None:
            self.calls = []

        async def prefetch_clip(self, request) -> None:
            self.calls.append(request.response.recording_id)
            if len(self.calls) == 1:
                raise RuntimeError("temporary")

    async def scenario() -> None:
        processor = Processor()
        worker = ClipPrefetchWorker(processor)
        await worker.start()
        first = _request()
        second = _request()
        assert await worker.enqueue(first)
        assert not await worker.enqueue(first)
        assert await worker.enqueue(second)
        await asyncio.wait_for(worker._queue.join(), timeout=2)
        assert processor.calls == [
            first.response.recording_id,
            second.response.recording_id,
        ]
        await worker.stop()

    asyncio.run(scenario())
