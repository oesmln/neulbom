import asyncio
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

import pytest

from app.repositories.analysis import (
    AnalysisStatus,
    InvalidAnalysisStateError,
    SQLiteAnalysisRepository,
    StoredAnalysis,
)
from app.services.analysis_worker import (
    AnalysisAlreadyQueuedError,
    AnalysisCompleted,
    AnalysisModelUnavailableError,
    AnalysisNeedsRetry,
    AnalysisProcessingOutcome,
    AnalysisWorkerNotStartedError,
    SingleAnalysisWorker,
)


class FakeProcessor:
    def __init__(
        self,
        outcome: AnalysisProcessingOutcome,
    ) -> None:
        self.outcome = outcome
        self.processed_ids: list[UUID] = []

    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisProcessingOutcome:
        self.processed_ids.append(
            analysis.analysis_id,
        )
        return self.outcome


class FailingProcessor:
    def __init__(
        self,
        error: Exception,
    ) -> None:
        self.error = error

    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisProcessingOutcome:
        del analysis
        raise self.error


class ConcurrencyTrackingProcessor:
    def __init__(self) -> None:
        self.current_count = 0
        self.maximum_count = 0
        self.processed_ids: list[UUID] = []

    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisProcessingOutcome:
        self.current_count += 1
        self.maximum_count = max(
            self.maximum_count,
            self.current_count,
        )

        try:
            await asyncio.sleep(0.01)
            self.processed_ids.append(
                analysis.analysis_id,
            )
            return AnalysisCompleted(
                result_body={
                    "analysis_id": str(
                        analysis.analysis_id,
                    ),
                },
            )
        finally:
            self.current_count -= 1


def create_repository(
    tmp_path: Path,
) -> SQLiteAnalysisRepository:
    return SQLiteAnalysisRepository(
        tmp_path / "analyses.sqlite3",
    )


def create_pending_analysis(
    repository: SQLiteAnalysisRepository,
) -> UUID:
    analysis_id = uuid4()

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=uuid4(),
        request_body={
            "analysis_id": str(
                analysis_id,
            ),
        },
    )

    return analysis_id


def test_worker_completes_analysis(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = create_pending_analysis(
            repository,
        )
        result_body: dict[str, Any] = {
            "model_score": 0.75,
            "risk_flag": True,
        }
        processor = FakeProcessor(
            AnalysisCompleted(
                result_body=result_body,
            ),
        )
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=processor,
        )

        await worker.start()
        await worker.enqueue(
            analysis_id,
        )
        await worker.join()
        await worker.stop()

        stored = repository.get(
            analysis_id,
        )

        assert stored is not None
        assert stored.status == (
            AnalysisStatus.COMPLETED
        )
        assert stored.result_body == (
            result_body
        )
        assert processor.processed_ids == [
            analysis_id,
        ]

    asyncio.run(scenario())


def test_worker_marks_analysis_needs_retry(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = create_pending_analysis(
            repository,
        )
        retry_items = (
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        )
        processor = FakeProcessor(
            AnalysisNeedsRetry(
                reason_code=(
                    "AUDIO_URL_EXPIRED"
                ),
                retry_items=retry_items,
            ),
        )
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=processor,
        )

        await worker.start()
        await worker.enqueue(
            analysis_id,
        )
        await worker.join()
        await worker.stop()

        stored = repository.get(
            analysis_id,
        )

        assert stored is not None
        assert stored.status == (
            AnalysisStatus.NEEDS_RETRY
        )
        assert stored.retryable is True
        assert stored.reason_code == (
            "AUDIO_URL_EXPIRED"
        )
        assert stored.retry_items == retry_items

    asyncio.run(scenario())


def test_worker_runs_only_one_analysis_at_a_time(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_ids = [
            create_pending_analysis(
                repository,
            )
            for _ in range(3)
        ]
        processor = (
            ConcurrencyTrackingProcessor()
        )
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=processor,
        )

        await worker.start()

        for analysis_id in analysis_ids:
            await worker.enqueue(
                analysis_id,
            )

        await worker.join()
        await worker.stop()

        assert processor.maximum_count == 1
        assert processor.processed_ids == (
            analysis_ids
        )

        for analysis_id in analysis_ids:
            stored = repository.get(
                analysis_id,
            )
            assert stored is not None
            assert stored.status == (
                AnalysisStatus.COMPLETED
            )

    asyncio.run(scenario())


def test_worker_marks_unexpected_error_failed(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = create_pending_analysis(
            repository,
        )
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=FailingProcessor(
                RuntimeError(
                    "unexpected test error",
                ),
            ),
        )

        await worker.start()
        await worker.enqueue(
            analysis_id,
        )
        await worker.join()
        await worker.stop()

        stored = repository.get(
            analysis_id,
        )

        assert stored is not None
        assert stored.status == (
            AnalysisStatus.FAILED
        )
        assert stored.reason_code == (
            "INTERNAL_ERROR"
        )
        assert stored.retryable is False

    asyncio.run(scenario())


def test_worker_marks_model_error_failed(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = create_pending_analysis(
            repository,
        )
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=FailingProcessor(
                AnalysisModelUnavailableError(
                    "model unavailable",
                ),
            ),
        )

        await worker.start()
        await worker.enqueue(
            analysis_id,
        )
        await worker.join()
        await worker.stop()

        stored = repository.get(
            analysis_id,
        )

        assert stored is not None
        assert stored.status == (
            AnalysisStatus.FAILED
        )
        assert stored.reason_code == (
            "MODEL_UNAVAILABLE"
        )

    asyncio.run(scenario())


def test_enqueue_requires_started_worker(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = create_pending_analysis(
            repository,
        )
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=FakeProcessor(
                AnalysisCompleted(
                    result_body={
                        "completed": True,
                    },
                ),
            ),
        )

        with pytest.raises(
            AnalysisWorkerNotStartedError,
        ):
            await worker.enqueue(
                analysis_id,
            )

    asyncio.run(scenario())


def test_duplicate_queue_entry_is_rejected(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = create_pending_analysis(
            repository,
        )

        class BlockingProcessor:
            def __init__(self) -> None:
                self.release = asyncio.Event()

            async def process(
                self,
                analysis: StoredAnalysis,
            ) -> AnalysisProcessingOutcome:
                del analysis
                await self.release.wait()
                return AnalysisCompleted(
                    result_body={
                        "completed": True,
                    },
                )

        processor = BlockingProcessor()
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=processor,
        )

        await worker.start()
        await worker.enqueue(
            analysis_id,
        )

        with pytest.raises(
            AnalysisAlreadyQueuedError,
        ):
            await worker.enqueue(
                analysis_id,
            )

        processor.release.set()
        await worker.join()
        await worker.stop()

    asyncio.run(scenario())


def test_non_pending_analysis_cannot_be_queued(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = create_pending_analysis(
            repository,
        )
        repository.mark_processing(
            analysis_id,
        )
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=FakeProcessor(
                AnalysisCompleted(
                    result_body={
                        "completed": True,
                    },
                ),
            ),
        )

        await worker.start()

        with pytest.raises(
            InvalidAnalysisStateError,
        ):
            await worker.enqueue(
                analysis_id,
            )

        await worker.stop()

    asyncio.run(scenario())

def test_retry_can_be_queued_while_previous_attempt_finishes(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = create_pending_analysis(
            repository,
        )

        class TwoAttemptProcessor:
            def __init__(self) -> None:
                self.call_count = 0
                self.first_started = (
                    asyncio.Event()
                )
                self.release_first = (
                    asyncio.Event()
                )

            async def process(
                self,
                analysis: StoredAnalysis,
            ) -> AnalysisProcessingOutcome:
                del analysis

                self.call_count += 1
                attempt_number = (
                    self.call_count
                )

                if attempt_number == 1:
                    self.first_started.set()
                    await self.release_first.wait()

                return AnalysisCompleted(
                    result_body={
                        "attempt": (
                            attempt_number
                        ),
                    },
                )

        processor = TwoAttemptProcessor()
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=processor,
        )

        await worker.start()
        await worker.enqueue(
            analysis_id,
        )

        await processor.first_started.wait()

        processing = repository.get(
            analysis_id,
        )

        assert processing is not None
        assert processing.status == (
            AnalysisStatus.PROCESSING
        )

        repository.mark_needs_retry(
            analysis_id=analysis_id,
            reason_code="AUDIO_URL_EXPIRED",
            retry_items=(
                {
                    "question_code": (
                        "orientation_year"
                    ),
                    "reason_code": (
                        "AUDIO_URL_EXPIRED"
                    ),
                    "required_action": (
                        "REISSUE_AUDIO_URL"
                    ),
                },
            ),
        )

        needs_retry = repository.get(
            analysis_id,
        )

        assert needs_retry is not None

        repository.resume_with_request(
            analysis_id=analysis_id,
            updated_request_body=(
                needs_retry.request_body
            ),
        )

        await worker.enqueue(
            analysis_id,
        )

        processor.release_first.set()

        await worker.join()
        await worker.stop()

        stored = repository.get(
            analysis_id,
        )

        assert stored is not None
        assert stored.status == (
            AnalysisStatus.COMPLETED
        )
        assert stored.result_body == {
            "attempt": 2,
        }
        assert processor.call_count == 2

    asyncio.run(scenario())