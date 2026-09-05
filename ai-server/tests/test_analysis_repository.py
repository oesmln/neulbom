from pathlib import Path
from uuid import uuid4

import pytest

from app.repositories.analysis import (
    AnalysisAlreadyExistsError,
    AnalysisNotFoundError,
    AnalysisStatus,
    InvalidAnalysisStateError,
    SQLiteAnalysisRepository,
)


def create_repository(
    tmp_path: Path,
) -> SQLiteAnalysisRepository:
    return SQLiteAnalysisRepository(
        tmp_path / "analyses.sqlite3",
    )


def test_create_and_get_pending_analysis(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    analysis_id = uuid4()
    assessment_id = uuid4()
    request_body = {
        "analysis_id": str(analysis_id),
        "assessment_id": str(assessment_id),
        "responses": [],
    }

    created = repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=assessment_id,
        request_body=request_body,
    )

    loaded = repository.get(
        analysis_id,
    )

    assert created.status == (
        AnalysisStatus.PENDING
    )
    assert loaded == created
    assert loaded is not None
    assert loaded.analysis_id == analysis_id
    assert loaded.assessment_id == assessment_id
    assert loaded.request_body == request_body
    assert loaded.retryable is False
    assert loaded.reason_code is None
    assert loaded.retry_items == ()
    assert loaded.result_body is None
    assert loaded.created_at.tzinfo is not None
    assert loaded.updated_at.tzinfo is not None


def test_duplicate_analysis_id_is_rejected(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    analysis_id = uuid4()

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=uuid4(),
        request_body={"attempt": 1},
    )

    with pytest.raises(
        AnalysisAlreadyExistsError,
    ):
        repository.create_pending(
            analysis_id=analysis_id,
            assessment_id=uuid4(),
            request_body={"attempt": 2},
        )


def test_pending_analysis_can_complete(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    analysis_id = uuid4()

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=uuid4(),
        request_body={"request": "value"},
    )

    processing = repository.mark_processing(
        analysis_id,
    )
    completed = repository.mark_completed(
        analysis_id=analysis_id,
        result_body={
            "model_score": 0.75,
            "risk_flag": True,
        },
    )

    assert processing.status == (
        AnalysisStatus.PROCESSING
    )
    assert completed.status == (
        AnalysisStatus.COMPLETED
    )
    assert completed.retryable is False
    assert completed.reason_code is None
    assert completed.retry_items == ()
    assert completed.result_body == {
        "model_score": 0.75,
        "risk_flag": True,
    }
    assert (
        completed.updated_at
        >= processing.updated_at
    )


def test_processing_analysis_can_need_retry(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    analysis_id = uuid4()

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=uuid4(),
        request_body={"request": "value"},
    )
    repository.mark_processing(
        analysis_id,
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

    stored = repository.mark_needs_retry(
        analysis_id=analysis_id,
        reason_code="AUDIO_URL_EXPIRED",
        retry_items=retry_items,
    )

    assert stored.status == (
        AnalysisStatus.NEEDS_RETRY
    )
    assert stored.retryable is True
    assert stored.reason_code == (
        "AUDIO_URL_EXPIRED"
    )
    assert stored.retry_items == retry_items
    assert stored.result_body is None


def test_processing_analysis_can_fail(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    analysis_id = uuid4()

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=uuid4(),
        request_body={"request": "value"},
    )
    repository.mark_processing(
        analysis_id,
    )

    stored = repository.mark_failed(
        analysis_id=analysis_id,
        reason_code="INTERNAL_ERROR",
    )

    assert stored.status == (
        AnalysisStatus.FAILED
    )
    assert stored.retryable is False
    assert stored.reason_code == (
        "INTERNAL_ERROR"
    )
    assert stored.retry_items == ()
    assert stored.result_body is None


def test_invalid_state_transition_is_rejected(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    analysis_id = uuid4()

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=uuid4(),
        request_body={"request": "value"},
    )

    with pytest.raises(
        InvalidAnalysisStateError,
    ):
        repository.mark_completed(
            analysis_id=analysis_id,
            result_body={
                "model_score": 0.5,
            },
        )


def test_unknown_analysis_is_rejected(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )

    with pytest.raises(
        AnalysisNotFoundError,
    ):
        repository.mark_processing(
            uuid4(),
        )


def test_needs_retry_requires_retry_items(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    analysis_id = uuid4()

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=uuid4(),
        request_body={"request": "value"},
    )
    repository.mark_processing(
        analysis_id,
    )

    with pytest.raises(
        ValueError,
        match="재시도 항목",
    ):
        repository.mark_needs_retry(
            analysis_id=analysis_id,
            reason_code=(
                "INCOMPLETE_ASSESSMENT"
            ),
            retry_items=(),
        )


def test_repository_data_survives_recreation(
    tmp_path: Path,
) -> None:
    database_path = (
        tmp_path / "analyses.sqlite3"
    )
    analysis_id = uuid4()
    assessment_id = uuid4()

    first_repository = (
        SQLiteAnalysisRepository(
            database_path,
        )
    )
    first_repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=assessment_id,
        request_body={"stored": True},
    )

    second_repository = (
        SQLiteAnalysisRepository(
            database_path,
        )
    )
    loaded = second_repository.get(
        analysis_id,
    )

    assert loaded is not None
    assert loaded.analysis_id == analysis_id
    assert loaded.assessment_id == assessment_id
    assert loaded.request_body == {
        "stored": True,
    }