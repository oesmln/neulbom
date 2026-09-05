from pathlib import Path

import pytest

from app.api.errors import APIError
from app.repositories.idempotency import (
    ReservationOutcome,
    SQLiteIdempotencyRepository,
    StoredHttpResponse,
)
from app.services.idempotency import (
    IdempotencyService,
    InvalidIdempotencyKeyError,
    create_request_hash,
)

IDEMPOTENCY_KEY = "test-idempotency-key-0001"
SCOPE = "recognition-plan:test-assessment"


def test_request_hash_ignores_json_key_order() -> None:
    first = {
        "question_set_version": "cist-v1",
        "response": {
            "question_code": (
                "memory_delayed_free_recall"
            ),
            "raw_transcript": "민수 공원",
        },
    }
    second = {
        "response": {
            "raw_transcript": "민수 공원",
            "question_code": (
                "memory_delayed_free_recall"
            ),
        },
        "question_set_version": "cist-v1",
    }

    assert create_request_hash(first) == (
        create_request_hash(second)
    )


def test_replays_same_request_without_reexecution(
    tmp_path: Path,
) -> None:
    service = _create_service(tmp_path)
    execution_count = 0

    def operation() -> StoredHttpResponse:
        nonlocal execution_count
        execution_count += 1

        return StoredHttpResponse(
            status_code=200,
            body={
                "status": "completed",
            },
            headers={
                "X-Test": "value",
            },
        )

    first = service.execute(
        scope=SCOPE,
        idempotency_key=IDEMPOTENCY_KEY,
        request_body={
            "value": 1,
        },
        operation=operation,
    )
    second = service.execute(
        scope=SCOPE,
        idempotency_key=IDEMPOTENCY_KEY,
        request_body={
            "value": 1,
        },
        operation=operation,
    )

    assert execution_count == 1
    assert first.replayed is False
    assert second.replayed is True
    assert second.response.status_code == 200
    assert second.response.body == {
        "status": "completed",
    }
    assert second.response.headers == {
        "X-Test": "value",
    }


def test_rejects_same_key_with_different_body(
    tmp_path: Path,
) -> None:
    service = _create_service(tmp_path)

    service.execute(
        scope=SCOPE,
        idempotency_key=IDEMPOTENCY_KEY,
        request_body={
            "value": 1,
        },
        operation=_successful_operation,
    )

    with pytest.raises(APIError) as captured:
        service.execute(
            scope=SCOPE,
            idempotency_key=IDEMPOTENCY_KEY,
            request_body={
                "value": 2,
            },
            operation=_successful_operation,
        )

    assert captured.value.status_code == 409
    assert captured.value.code == (
        "IDEMPOTENCY_CONFLICT"
    )
    assert captured.value.retryable is False


def test_abandons_reservation_after_failure(
    tmp_path: Path,
) -> None:
    service = _create_service(tmp_path)

    def failing_operation() -> StoredHttpResponse:
        raise RuntimeError("temporary failure")

    with pytest.raises(RuntimeError):
        service.execute(
            scope=SCOPE,
            idempotency_key=IDEMPOTENCY_KEY,
            request_body={
                "value": 1,
            },
            operation=failing_operation,
        )

    result = service.execute(
        scope=SCOPE,
        idempotency_key=IDEMPOTENCY_KEY,
        request_body={
            "value": 1,
        },
        operation=_successful_operation,
    )

    assert result.replayed is False
    assert result.response.status_code == 200


def test_detects_request_already_in_progress(
    tmp_path: Path,
) -> None:
    repository = SQLiteIdempotencyRepository(
        tmp_path / "idempotency.sqlite3",
    )
    service = IdempotencyService(repository)

    request_body = {
        "value": 1,
    }
    request_hash = create_request_hash(
        request_body,
    )

    reservation = repository.reserve(
        scope=SCOPE,
        idempotency_key=IDEMPOTENCY_KEY,
        request_hash=request_hash,
    )

    assert reservation.outcome == (
        ReservationOutcome.NEW
    )

    with pytest.raises(APIError) as captured:
        service.execute(
            scope=SCOPE,
            idempotency_key=IDEMPOTENCY_KEY,
            request_body=request_body,
            operation=_successful_operation,
        )

    assert captured.value.status_code == 409
    assert captured.value.code == (
        "INVALID_ANALYSIS_STATE"
    )
    assert captured.value.retryable is True


def test_persists_completed_response_between_instances(
    tmp_path: Path,
) -> None:
    database_path = (
        tmp_path / "idempotency.sqlite3"
    )

    first_service = IdempotencyService(
        SQLiteIdempotencyRepository(
            database_path,
        ),
    )
    first_service.execute(
        scope=SCOPE,
        idempotency_key=IDEMPOTENCY_KEY,
        request_body={
            "value": 1,
        },
        operation=_successful_operation,
    )

    second_service = IdempotencyService(
        SQLiteIdempotencyRepository(
            database_path,
        ),
    )
    result = second_service.execute(
        scope=SCOPE,
        idempotency_key=IDEMPOTENCY_KEY,
        request_body={
            "value": 1,
        },
        operation=lambda: pytest.fail(
            "기존 응답을 재사용해야 합니다.",
        ),
    )

    assert result.replayed is True
    assert result.response.body == {
        "status": "completed",
    }


def test_rejects_short_idempotency_key(
    tmp_path: Path,
) -> None:
    service = _create_service(tmp_path)

    with pytest.raises(
        InvalidIdempotencyKeyError,
        match="16자 이상",
    ):
        service.execute(
            scope=SCOPE,
            idempotency_key="short",
            request_body={
                "value": 1,
            },
            operation=_successful_operation,
        )


def _create_service(
    tmp_path: Path,
) -> IdempotencyService:
    repository = SQLiteIdempotencyRepository(
        tmp_path / "idempotency.sqlite3",
    )
    return IdempotencyService(repository)


def _successful_operation() -> StoredHttpResponse:
    return StoredHttpResponse(
        status_code=200,
        body={
            "status": "completed",
        },
        headers={},
    )