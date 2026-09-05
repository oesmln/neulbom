from pathlib import Path
from uuid import uuid4

import pytest

from app.repositories.analysis import (
    AnalysisNotFoundError,
    AnalysisRepositoryError,
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


def create_needs_retry_analysis(
    repository: SQLiteAnalysisRepository,
):
    analysis_id = uuid4()
    assessment_id = uuid4()
    original_request = {
        "analysis_id": str(analysis_id),
        "assessment_id": str(
            assessment_id,
        ),
        "responses": [
            {
                "question_code": (
                    "orientation_year"
                ),
                "audio": {
                    "signed_url": (
                        "https://old.example/audio"
                    ),
                },
            },
        ],
    }

    repository.create_pending(
        analysis_id=analysis_id,
        assessment_id=assessment_id,
        request_body=original_request,
    )
    repository.mark_processing(
        analysis_id,
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

    return (
        analysis_id,
        assessment_id,
        original_request,
    )


def test_resumes_needs_retry_analysis(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    (
        analysis_id,
        assessment_id,
        _original_request,
    ) = create_needs_retry_analysis(
        repository,
    )
    updated_request = {
        "analysis_id": str(analysis_id),
        "assessment_id": str(
            assessment_id,
        ),
        "responses": [
            {
                "question_code": (
                    "orientation_year"
                ),
                "audio": {
                    "signed_url": (
                        "https://new.example/audio"
                    ),
                },
            },
        ],
    }

    resumed = repository.resume_with_request(
        analysis_id=analysis_id,
        updated_request_body=(
            updated_request
        ),
    )

    assert resumed.analysis_id == analysis_id
    assert (
        resumed.assessment_id
        == assessment_id
    )
    assert resumed.status == (
        AnalysisStatus.PENDING
    )
    assert resumed.request_body == (
        updated_request
    )
    assert resumed.retryable is False
    assert resumed.reason_code is None
    assert resumed.retry_items == ()
    assert resumed.result_body is None


def test_archives_previous_attempt(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    (
        analysis_id,
        assessment_id,
        original_request,
    ) = create_needs_retry_analysis(
        repository,
    )
    updated_request = {
        "analysis_id": str(analysis_id),
        "assessment_id": str(
            assessment_id,
        ),
        "attempt": 2,
    }

    repository.resume_with_request(
        analysis_id=analysis_id,
        updated_request_body=(
            updated_request
        ),
    )

    history = (
        repository.list_archived_attempts(
            analysis_id,
        )
    )

    assert len(history) == 1

    first_attempt = history[0]

    assert first_attempt.attempt_number == 1
    assert first_attempt.status == (
        AnalysisStatus.NEEDS_RETRY
    )
    assert first_attempt.request_body == (
        original_request
    )
    assert first_attempt.retryable is True
    assert first_attempt.reason_code == (
        "AUDIO_URL_EXPIRED"
    )
    assert first_attempt.retry_items == (
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
    assert first_attempt.result_body is None
    assert (
        first_attempt.archived_at.tzinfo
        is not None
    )


def test_preserves_multiple_retry_attempts(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    (
        analysis_id,
        assessment_id,
        _original_request,
    ) = create_needs_retry_analysis(
        repository,
    )

    second_request = {
        "analysis_id": str(analysis_id),
        "assessment_id": str(
            assessment_id,
        ),
        "attempt": 2,
    }

    repository.resume_with_request(
        analysis_id=analysis_id,
        updated_request_body=(
            second_request
        ),
    )
    repository.mark_processing(
        analysis_id,
    )
    repository.mark_needs_retry(
        analysis_id=analysis_id,
        reason_code="UNSCORABLE_STT",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "UNSCORABLE_STT"
                ),
                "required_action": (
                    "REPLACE_RESPONSE"
                ),
            },
        ),
    )

    third_request = {
        "analysis_id": str(analysis_id),
        "assessment_id": str(
            assessment_id,
        ),
        "attempt": 3,
    }

    repository.resume_with_request(
        analysis_id=analysis_id,
        updated_request_body=(
            third_request
        ),
    )

    history = (
        repository.list_archived_attempts(
            analysis_id,
        )
    )

    assert [
        attempt.attempt_number
        for attempt in history
    ] == [1, 2]
    assert history[0].reason_code == (
        "AUDIO_URL_EXPIRED"
    )
    assert history[1].reason_code == (
        "UNSCORABLE_STT"
    )
    assert history[1].request_body == (
        second_request
    )

    current = repository.get(
        analysis_id,
    )

    assert current is not None
    assert current.status == (
        AnalysisStatus.PENDING
    )
    assert current.request_body == (
        third_request
    )


def test_cannot_retry_pending_analysis(
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
        InvalidAnalysisStateError,
        match="needs_retry",
    ):
        repository.resume_with_request(
            analysis_id=analysis_id,
            updated_request_body={
                "attempt": 2,
            },
        )

    assert (
        repository.list_archived_attempts(
            analysis_id,
        )
        == ()
    )


def test_unknown_analysis_cannot_retry(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )

    with pytest.raises(
        AnalysisNotFoundError,
    ):
        repository.resume_with_request(
            analysis_id=uuid4(),
            updated_request_body={
                "attempt": 2,
            },
        )


def test_invalid_updated_request_is_rejected(
    tmp_path: Path,
) -> None:
    repository = create_repository(
        tmp_path,
    )
    (
        analysis_id,
        _assessment_id,
        original_request,
    ) = create_needs_retry_analysis(
        repository,
    )

    with pytest.raises(
        AnalysisRepositoryError,
    ):
        repository.resume_with_request(
            analysis_id=analysis_id,
            updated_request_body={
                "invalid": {
                    1,
                    2,
                    3,
                },
            },
        )

    stored = repository.get(
        analysis_id,
    )

    assert stored is not None
    assert stored.status == (
        AnalysisStatus.NEEDS_RETRY
    )
    assert stored.request_body == (
        original_request
    )
    assert (
        repository.list_archived_attempts(
            analysis_id,
        )
        == ()
    )


def test_history_survives_repository_recreation(
    tmp_path: Path,
) -> None:
    database_path = (
        tmp_path / "analyses.sqlite3"
    )
    first_repository = (
        SQLiteAnalysisRepository(
            database_path,
        )
    )
    (
        analysis_id,
        assessment_id,
        _original_request,
    ) = create_needs_retry_analysis(
        first_repository,
    )

    first_repository.resume_with_request(
        analysis_id=analysis_id,
        updated_request_body={
            "analysis_id": str(
                analysis_id,
            ),
            "assessment_id": str(
                assessment_id,
            ),
            "attempt": 2,
        },
    )

    second_repository = (
        SQLiteAnalysisRepository(
            database_path,
        )
    )
    history = (
        second_repository
        .list_archived_attempts(
            analysis_id,
        )
    )

    assert len(history) == 1
    assert history[0].attempt_number == 1
    assert history[0].analysis_id == (
        analysis_id
    )