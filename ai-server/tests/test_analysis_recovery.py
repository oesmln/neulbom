import asyncio
from pathlib import Path
from uuid import UUID, uuid4

import pytest

from app.repositories.analysis import (
    AnalysisStatus,
    SQLiteAnalysisRepository,
    StoredAnalysis,
)
from app.services.analysis_worker import (
    AnalysisCompleted,
    SingleAnalysisWorker,
)


class CompletingProcessor:
    def __init__(self) -> None:
        self.processed_ids: list[UUID] = []

    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisCompleted:
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


class BlockingProcessor:
    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisCompleted:
        del analysis

        event = asyncio.Event()
        await event.wait()

        raise AssertionError(
            "제한 시간 이후 실행될 수 없습니다.",
        )


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


def test_recovers_pending_and_processing_jobs(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    pending_id = create_pending_analysis(
        repository,
    )
    processing_id = create_pending_analysis(
        repository,
    )
    retry_id = create_pending_analysis(
        repository,
    )
    completed_id = create_pending_analysis(
        repository,
    )

    repository.mark_processing(
        processing_id,
    )

    repository.mark_processing(
        retry_id,
    )
    repository.mark_needs_retry(
        analysis_id=retry_id,
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

    repository.mark_processing(
        completed_id,
    )
    repository.mark_completed(
        analysis_id=completed_id,
        result_body={
            "completed": True,
        },
    )

    recovered_ids = (
        repository.recover_incomplete()
    )

    assert set(recovered_ids) == {
        pending_id,
        processing_id,
    }

    recovered_processing = repository.get(
        processing_id,
    )
    retry_analysis = repository.get(
        retry_id,
    )
    completed_analysis = repository.get(
        completed_id,
    )

    assert recovered_processing is not None
    assert recovered_processing.status == (
        AnalysisStatus.PENDING
    )
    assert recovered_processing.retryable is False
    assert (
        recovered_processing.reason_code
        is None
    )
    assert (
        recovered_processing.retry_items
        == ()
    )

    assert retry_analysis is not None
    assert retry_analysis.status == (
        AnalysisStatus.NEEDS_RETRY
    )

    assert completed_analysis is not None
    assert completed_analysis.status == (
        AnalysisStatus.COMPLETED
    )


def test_worker_processes_recovered_jobs(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        pending_id = create_pending_analysis(
            repository,
        )
        processing_id = (
            create_pending_analysis(
                repository,
            )
        )
        repository.mark_processing(
            processing_id,
        )

        recovered_ids = (
            repository.recover_incomplete()
        )
        processor = CompletingProcessor()
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=processor,
            processing_timeout_seconds=1.0,
        )

        await worker.start()

        for analysis_id in recovered_ids:
            await worker.enqueue(
                analysis_id,
            )

        await worker.join()
        await worker.stop()

        assert set(
            processor.processed_ids,
        ) == {
            pending_id,
            processing_id,
        }

        for analysis_id in recovered_ids:
            stored = repository.get(
                analysis_id,
            )

            assert stored is not None
            assert stored.status == (
                AnalysisStatus.COMPLETED
            )

    asyncio.run(scenario())


def test_processing_timeout_marks_job_failed(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        repository = create_repository(
            tmp_path,
        )
        analysis_id = (
            create_pending_analysis(
                repository,
            )
        )
        worker = SingleAnalysisWorker(
            repository=repository,
            processor=BlockingProcessor(),
            processing_timeout_seconds=0.01,
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


def test_rejects_non_positive_timeout(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )

    with pytest.raises(
        ValueError,
        match="제한 시간",
    ):
        SingleAnalysisWorker(
            repository=repository,
            processor=CompletingProcessor(),
            processing_timeout_seconds=0,
        )