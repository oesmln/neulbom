import json
import sqlite3
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import UTC, datetime
from enum import StrEnum
from pathlib import Path
from typing import Any, Iterator, Protocol


class ReservationOutcome(StrEnum):
    NEW = "new"
    REPLAY = "replay"
    CONFLICT = "conflict"
    IN_PROGRESS = "in_progress"


@dataclass(frozen=True, slots=True)
class StoredHttpResponse:
    status_code: int
    body: dict[str, Any]
    headers: dict[str, str]


@dataclass(frozen=True, slots=True)
class ReservationResult:
    outcome: ReservationOutcome
    response: StoredHttpResponse | None = None


class IdempotencyRepositoryError(RuntimeError):
    """멱등 저장소의 상태가 올바르지 않을 때 발생한다."""


class IdempotencyRepository(Protocol):
    def reserve(
        self,
        *,
        scope: str,
        idempotency_key: str,
        request_hash: str,
    ) -> ReservationResult:
        """요청을 예약하거나 기존 상태를 조회한다."""
        ...

    def complete(
        self,
        *,
        scope: str,
        idempotency_key: str,
        request_hash: str,
        response: StoredHttpResponse,
    ) -> None:
        """처리가 끝난 요청의 응답을 저장한다."""
        ...

    def abandon(
        self,
        *,
        scope: str,
        idempotency_key: str,
        request_hash: str,
    ) -> None:
        """처리 실패한 pending 예약을 제거한다."""
        ...


class SQLiteIdempotencyRepository:
    """SQLite 기반 멱등 요청 저장소."""

    def __init__(
        self,
        database_path: Path,
    ) -> None:
        self._database_path = (
            database_path.resolve()
        )
        self._database_path.parent.mkdir(
            parents=True,
            exist_ok=True,
        )
        self._initialize_database()

    def reserve(
        self,
        *,
        scope: str,
        idempotency_key: str,
        request_hash: str,
    ) -> ReservationResult:
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")

            row = connection.execute(
                """
                SELECT
                    request_hash,
                    state,
                    status_code,
                    response_body,
                    response_headers
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                """,
                (
                    scope,
                    idempotency_key,
                ),
            ).fetchone()

            if row is None:
                now = _current_timestamp()

                connection.execute(
                    """
                    INSERT INTO idempotency_records (
                        scope,
                        idempotency_key,
                        request_hash,
                        state,
                        status_code,
                        response_body,
                        response_headers,
                        created_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, 'pending', NULL, NULL, NULL, ?, ?)
                    """,
                    (
                        scope,
                        idempotency_key,
                        request_hash,
                        now,
                        now,
                    ),
                )

                return ReservationResult(
                    outcome=ReservationOutcome.NEW,
                )

            if row["request_hash"] != request_hash:
                return ReservationResult(
                    outcome=(
                        ReservationOutcome.CONFLICT
                    ),
                )

            if row["state"] == "pending":
                return ReservationResult(
                    outcome=(
                        ReservationOutcome.IN_PROGRESS
                    ),
                )

            if row["state"] == "completed":
                return ReservationResult(
                    outcome=ReservationOutcome.REPLAY,
                    response=_deserialize_response(row),
                )

            raise IdempotencyRepositoryError(
                "알 수 없는 멱등 요청 상태입니다: "
                f"{row['state']!r}",
            )

    def complete(
        self,
        *,
        scope: str,
        idempotency_key: str,
        request_hash: str,
        response: StoredHttpResponse,
    ) -> None:
        response_body = json.dumps(
            response.body,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        )
        response_headers = json.dumps(
            response.headers,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )

        with self._connection() as connection:
            cursor = connection.execute(
                """
                UPDATE idempotency_records
                SET state = 'completed',
                    status_code = ?,
                    response_body = ?,
                    response_headers = ?,
                    updated_at = ?
                WHERE scope = ?
                  AND idempotency_key = ?
                  AND request_hash = ?
                  AND state = 'pending'
                """,
                (
                    response.status_code,
                    response_body,
                    response_headers,
                    _current_timestamp(),
                    scope,
                    idempotency_key,
                    request_hash,
                ),
            )

            if cursor.rowcount != 1:
                raise IdempotencyRepositoryError(
                    "완료할 pending 멱등 요청을 "
                    "찾을 수 없습니다.",
                )

    def abandon(
        self,
        *,
        scope: str,
        idempotency_key: str,
        request_hash: str,
    ) -> None:
        with self._connection() as connection:
            connection.execute(
                """
                DELETE FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                  AND request_hash = ?
                  AND state = 'pending'
                """,
                (
                    scope,
                    idempotency_key,
                    request_hash,
                ),
            )

    def _initialize_database(self) -> None:
        with self._connection() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS idempotency_records (
                    scope TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL,
                    request_hash TEXT NOT NULL,
                    state TEXT NOT NULL
                        CHECK (state IN ('pending', 'completed')),
                    status_code INTEGER,
                    response_body TEXT,
                    response_headers TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (scope, idempotency_key)
                )
                """,
            )

    @contextmanager
    def _connection(
        self,
    ) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(
            self._database_path,
            timeout=5,
        )
        connection.row_factory = sqlite3.Row

        try:
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()


def _deserialize_response(
    row: sqlite3.Row,
) -> StoredHttpResponse:
    status_code = row["status_code"]
    response_body = row["response_body"]
    response_headers = row["response_headers"]

    if (
        not isinstance(status_code, int)
        or not isinstance(response_body, str)
        or not isinstance(response_headers, str)
    ):
        raise IdempotencyRepositoryError(
            "완료된 멱등 요청의 응답 데이터가 "
            "올바르지 않습니다.",
        )

    body = json.loads(response_body)
    headers = json.loads(response_headers)

    if not isinstance(body, dict):
        raise IdempotencyRepositoryError(
            "저장된 응답 body는 JSON 객체여야 합니다.",
        )

    if not isinstance(headers, dict):
        raise IdempotencyRepositoryError(
            "저장된 응답 headers는 JSON 객체여야 합니다.",
        )

    return StoredHttpResponse(
        status_code=status_code,
        body=body,
        headers={
            str(key): str(value)
            for key, value in headers.items()
        },
    )


def _current_timestamp() -> str:
    return datetime.now(UTC).isoformat()