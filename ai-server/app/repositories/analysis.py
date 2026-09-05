import json
import sqlite3
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import UTC, datetime
from enum import StrEnum
from pathlib import Path
from typing import Any, Iterator, Protocol
from uuid import UUID


class AnalysisStatus(StrEnum):
    PENDING = "pending"
    PROCESSING = "processing"
    NEEDS_RETRY = "needs_retry"
    COMPLETED = "completed"
    FAILED = "failed"


@dataclass(frozen=True, slots=True)
class StoredAnalysis:
    analysis_id: UUID
    assessment_id: UUID
    status: AnalysisStatus
    request_body: dict[str, Any]
    retryable: bool
    reason_code: str | None
    retry_items: tuple[dict[str, Any], ...]
    result_body: dict[str, Any] | None
    created_at: datetime
    updated_at: datetime

@dataclass(frozen=True, slots=True)
class ArchivedAnalysisAttempt:
    analysis_id: UUID
    attempt_number: int
    status: AnalysisStatus
    request_body: dict[str, Any]
    retryable: bool
    reason_code: str | None
    retry_items: tuple[
        dict[str, Any],
        ...,
    ]
    result_body: dict[str, Any] | None
    created_at: datetime
    updated_at: datetime
    archived_at: datetime


class AnalysisRepositoryError(RuntimeError):
    """분석 작업 저장소 처리 중 발생하는 오류."""


class AnalysisAlreadyExistsError(
    AnalysisRepositoryError,
):
    """동일한 analysis_id가 이미 존재하는 경우."""


class AnalysisNotFoundError(
    AnalysisRepositoryError,
):
    """analysis_id에 해당하는 작업이 없는 경우."""


class InvalidAnalysisStateError(
    AnalysisRepositoryError,
):
    """현재 상태에서 허용하지 않는 상태 전이인 경우."""


class AnalysisRepository(Protocol):
    def create_pending(
        self,
        *,
        analysis_id: UUID,
        assessment_id: UUID,
        request_body: dict[str, Any],
    ) -> StoredAnalysis:
        ...

    def get(
        self,
        analysis_id: UUID,
    ) -> StoredAnalysis | None:
        ...

    def mark_processing(
        self,
        analysis_id: UUID,
    ) -> StoredAnalysis:
        ...

    def mark_completed(
        self,
        *,
        analysis_id: UUID,
        result_body: dict[str, Any],
    ) -> StoredAnalysis:
        ...

    def mark_needs_retry(
        self,
        *,
        analysis_id: UUID,
        reason_code: str,
        retry_items: tuple[
            dict[str, Any],
            ...,
        ],
    ) -> StoredAnalysis:
        ...

    def mark_failed(
        self,
        *,
        analysis_id: UUID,
        reason_code: str,
    ) -> StoredAnalysis:
        ...

    def resume_with_request(
        self,
        *,
        analysis_id: UUID,
        updated_request_body: dict[
            str,
            Any,
        ],
    ) -> StoredAnalysis:
        ...

    def list_archived_attempts(
        self,
        analysis_id: UUID,
    ) -> tuple[
        ArchivedAnalysisAttempt,
        ...,
    ]:
        ...

    def recover_incomplete(
        self,
    ) -> tuple[UUID, ...]:
        ...

class SQLiteAnalysisRepository:
    """비동기 분석 작업을 저장하는 SQLite 저장소."""

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

    def create_pending(
        self,
        *,
        analysis_id: UUID,
        assessment_id: UUID,
        request_body: dict[str, Any],
    ) -> StoredAnalysis:
        timestamp = _current_timestamp()

        try:
            serialized_request = _serialize_json(
                request_body,
            )
        except (TypeError, ValueError) as error:
            raise AnalysisRepositoryError(
                "분석 요청을 JSON으로 저장할 수 없습니다.",
            ) from error

        try:
            with self._connection() as connection:
                connection.execute(
                    """
                    INSERT INTO analysis_jobs (
                        analysis_id,
                        assessment_id,
                        status,
                        request_body,
                        retryable,
                        reason_code,
                        retry_items,
                        result_body,
                        created_at,
                        updated_at
                    )
                    VALUES (?, ?, 'pending', ?, 0, NULL, '[]', NULL, ?, ?)
                    """,
                    (
                        str(analysis_id),
                        str(assessment_id),
                        serialized_request,
                        timestamp,
                        timestamp,
                    ),
                )
        except sqlite3.IntegrityError as error:
            raise AnalysisAlreadyExistsError(
                "동일한 analysis_id의 분석 작업이 "
                "이미 존재합니다.",
            ) from error

        return self._require_analysis(
            analysis_id,
        )

    def get(
        self,
        analysis_id: UUID,
    ) -> StoredAnalysis | None:
        with self._connection() as connection:
            row = connection.execute(
                """
                SELECT
                    analysis_id,
                    assessment_id,
                    status,
                    request_body,
                    retryable,
                    reason_code,
                    retry_items,
                    result_body,
                    created_at,
                    updated_at
                FROM analysis_jobs
                WHERE analysis_id = ?
                """,
                (str(analysis_id),),
            ).fetchone()

        if row is None:
            return None

        return _row_to_analysis(row)

    def resume_with_request(
        self,
        *,
        analysis_id: UUID,
        updated_request_body: dict[
            str,
            Any,
        ],
    ) -> StoredAnalysis:
        try:
            serialized_request = (
                _serialize_json(
                    updated_request_body,
                )
            )
        except (TypeError, ValueError) as error:
            raise AnalysisRepositoryError(
                "갱신된 분석 요청을 JSON으로 "
                "저장할 수 없습니다.",
            ) from error

        timestamp = _current_timestamp()

        with self._connection() as connection:
            connection.execute(
                "BEGIN IMMEDIATE",
            )

            row = connection.execute(
                """
                SELECT
                    analysis_id,
                    assessment_id,
                    status,
                    request_body,
                    retryable,
                    reason_code,
                    retry_items,
                    result_body,
                    created_at,
                    updated_at
                FROM analysis_jobs
                WHERE analysis_id = ?
                """,
                (str(analysis_id),),
            ).fetchone()

            if row is None:
                raise AnalysisNotFoundError(
                    "분석 작업을 찾을 수 없습니다.",
                )

            if (
                row["status"]
                != AnalysisStatus.NEEDS_RETRY.value
            ):
                raise InvalidAnalysisStateError(
                    "needs_retry 상태의 분석만 "
                    "재시도할 수 있습니다: "
                    f"status={row['status']}",
                )

            attempt_row = connection.execute(
                """
                SELECT COALESCE(
                    MAX(attempt_number),
                    0
                ) AS maximum_attempt
                FROM analysis_attempt_history
                WHERE analysis_id = ?
                """,
                (str(analysis_id),),
            ).fetchone()

            attempt_number = (
                int(
                    attempt_row[
                        "maximum_attempt"
                    ],
                )
                + 1
            )

            connection.execute(
                """
                INSERT INTO analysis_attempt_history (
                    analysis_id,
                    attempt_number,
                    status,
                    request_body,
                    retryable,
                    reason_code,
                    retry_items,
                    result_body,
                    created_at,
                    updated_at,
                    archived_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    row["analysis_id"],
                    attempt_number,
                    row["status"],
                    row["request_body"],
                    row["retryable"],
                    row["reason_code"],
                    row["retry_items"],
                    row["result_body"],
                    row["created_at"],
                    row["updated_at"],
                    timestamp,
                ),
            )

            cursor = connection.execute(
                """
                UPDATE analysis_jobs
                SET
                    status = 'pending',
                    request_body = ?,
                    retryable = 0,
                    reason_code = NULL,
                    retry_items = '[]',
                    result_body = NULL,
                    updated_at = ?
                WHERE analysis_id = ?
                  AND status = 'needs_retry'
                """,
                (
                    serialized_request,
                    timestamp,
                    str(analysis_id),
                ),
            )

            if cursor.rowcount != 1:
                raise InvalidAnalysisStateError(
                    "분석 재시도 상태 전이에 "
                    "실패했습니다.",
                )

        return self._require_analysis(
            analysis_id,
        )

    def list_archived_attempts(
        self,
        analysis_id: UUID,
    ) -> tuple[
        ArchivedAnalysisAttempt,
        ...,
    ]:
        with self._connection() as connection:
            rows = connection.execute(
                """
                SELECT
                    analysis_id,
                    attempt_number,
                    status,
                    request_body,
                    retryable,
                    reason_code,
                    retry_items,
                    result_body,
                    created_at,
                    updated_at,
                    archived_at
                FROM analysis_attempt_history
                WHERE analysis_id = ?
                ORDER BY attempt_number
                """,
                (str(analysis_id),),
            ).fetchall()

        return tuple(
            _row_to_archived_attempt(row)
            for row in rows
        )

    def recover_incomplete(
        self,
    ) -> tuple[UUID, ...]:
        """
        서버 종료로 중단된 processing 작업을 pending으로 복구하고
        다시 처리해야 할 모든 pending 작업 ID를 반환한다.
        """
        timestamp = _current_timestamp()

        with self._connection() as connection:
            connection.execute(
                "BEGIN IMMEDIATE",
            )
            connection.execute(
                """
                UPDATE analysis_jobs
                SET
                    status = 'pending',
                    retryable = 0,
                    reason_code = NULL,
                    retry_items = '[]',
                    result_body = NULL,
                    updated_at = ?
                WHERE status = 'processing'
                """,
                (timestamp,),
            )

            rows = connection.execute(
                """
                SELECT analysis_id
                FROM analysis_jobs
                WHERE status = 'pending'
                ORDER BY created_at, analysis_id
                """,
            ).fetchall()

        return tuple(
            UUID(row["analysis_id"])
            for row in rows
        )

    def mark_processing(
        self,
        analysis_id: UUID,
    ) -> StoredAnalysis:
        return self._transition(
            analysis_id=analysis_id,
            expected_status=AnalysisStatus.PENDING,
            next_status=AnalysisStatus.PROCESSING,
            retryable=False,
            reason_code=None,
            retry_items=(),
            result_body=None,
        )

    def mark_completed(
        self,
        *,
        analysis_id: UUID,
        result_body: dict[str, Any],
    ) -> StoredAnalysis:
        if not result_body:
            raise ValueError(
                "완료 상태에는 최종 분석 결과가 필요합니다.",
            )

        return self._transition(
            analysis_id=analysis_id,
            expected_status=(
                AnalysisStatus.PROCESSING
            ),
            next_status=AnalysisStatus.COMPLETED,
            retryable=False,
            reason_code=None,
            retry_items=(),
            result_body=result_body,
        )

    def mark_needs_retry(
        self,
        *,
        analysis_id: UUID,
        reason_code: str,
        retry_items: tuple[
            dict[str, Any],
            ...,
        ],
    ) -> StoredAnalysis:
        if not reason_code.strip():
            raise ValueError(
                "재시도 사유 코드가 필요합니다.",
            )

        if not retry_items:
            raise ValueError(
                "needs_retry 상태에는 "
                "재시도 항목이 필요합니다.",
            )

        return self._transition(
            analysis_id=analysis_id,
            expected_status=(
                AnalysisStatus.PROCESSING
            ),
            next_status=AnalysisStatus.NEEDS_RETRY,
            retryable=True,
            reason_code=reason_code,
            retry_items=retry_items,
            result_body=None,
        )

    def mark_failed(
        self,
        *,
        analysis_id: UUID,
        reason_code: str,
    ) -> StoredAnalysis:
        if not reason_code.strip():
            raise ValueError(
                "실패 사유 코드가 필요합니다.",
            )

        return self._transition(
            analysis_id=analysis_id,
            expected_status=(
                AnalysisStatus.PROCESSING
            ),
            next_status=AnalysisStatus.FAILED,
            retryable=False,
            reason_code=reason_code,
            retry_items=(),
            result_body=None,
        )

    def _transition(
        self,
        *,
        analysis_id: UUID,
        expected_status: AnalysisStatus,
        next_status: AnalysisStatus,
        retryable: bool,
        reason_code: str | None,
        retry_items: tuple[
            dict[str, Any],
            ...,
        ],
        result_body: dict[str, Any] | None,
    ) -> StoredAnalysis:
        timestamp = _current_timestamp()

        try:
            serialized_retry_items = (
                _serialize_json(
                    list(retry_items),
                )
            )
            serialized_result = (
                _serialize_json(result_body)
                if result_body is not None
                else None
            )
        except (TypeError, ValueError) as error:
            raise AnalysisRepositoryError(
                "분석 상태 데이터를 JSON으로 "
                "저장할 수 없습니다.",
            ) from error

        with self._connection() as connection:
            cursor = connection.execute(
                """
                UPDATE analysis_jobs
                SET
                    status = ?,
                    retryable = ?,
                    reason_code = ?,
                    retry_items = ?,
                    result_body = ?,
                    updated_at = ?
                WHERE analysis_id = ?
                  AND status = ?
                """,
                (
                    next_status.value,
                    int(retryable),
                    reason_code,
                    serialized_retry_items,
                    serialized_result,
                    timestamp,
                    str(analysis_id),
                    expected_status.value,
                ),
            )

            if cursor.rowcount != 1:
                row = connection.execute(
                    """
                    SELECT status
                    FROM analysis_jobs
                    WHERE analysis_id = ?
                    """,
                    (str(analysis_id),),
                ).fetchone()

                if row is None:
                    raise AnalysisNotFoundError(
                        "분석 작업을 찾을 수 없습니다.",
                    )

                raise InvalidAnalysisStateError(
                    "허용하지 않는 분석 상태 전이입니다: "
                    f"current={row['status']}, "
                    f"expected={expected_status.value}, "
                    f"next={next_status.value}",
                )

        return self._require_analysis(
            analysis_id,
        )

    def _require_analysis(
        self,
        analysis_id: UUID,
    ) -> StoredAnalysis:
        analysis = self.get(analysis_id)

        if analysis is None:
            raise AnalysisNotFoundError(
                "분석 작업을 찾을 수 없습니다.",
            )

        return analysis

    def _initialize_database(self) -> None:
        with self._connection() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS analysis_jobs (
                    analysis_id TEXT PRIMARY KEY,
                    assessment_id TEXT NOT NULL,
                    status TEXT NOT NULL CHECK (
                        status IN (
                            'pending',
                            'processing',
                            'needs_retry',
                            'completed',
                            'failed'
                        )
                    ),
                    request_body TEXT NOT NULL,
                    retryable INTEGER NOT NULL CHECK (
                        retryable IN (0, 1)
                    ),
                    reason_code TEXT,
                    retry_items TEXT NOT NULL,
                    result_body TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """,
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS
                    analysis_attempt_history (
                        analysis_id TEXT NOT NULL,
                        attempt_number INTEGER NOT NULL,
                        status TEXT NOT NULL CHECK (
                            status IN (
                                'pending',
                                'processing',
                                'needs_retry',
                                'completed',
                                'failed'
                            )
                        ),
                        request_body TEXT NOT NULL,
                        retryable INTEGER NOT NULL CHECK (
                            retryable IN (0, 1)
                        ),
                        reason_code TEXT,
                        retry_items TEXT NOT NULL,
                        result_body TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        archived_at TEXT NOT NULL,
                        PRIMARY KEY (
                            analysis_id,
                            attempt_number
                        ),
                        FOREIGN KEY (analysis_id)
                            REFERENCES analysis_jobs (
                                analysis_id
                            )
                            ON DELETE CASCADE
                    )
                """,
            )
            connection.execute(
                """
                CREATE INDEX IF NOT EXISTS
                    idx_analysis_attempt_history_id
                ON analysis_attempt_history (
                    analysis_id,
                    attempt_number
                )
                """,
            )
            connection.execute(
                """
                CREATE INDEX IF NOT EXISTS
                    idx_analysis_jobs_assessment_id
                ON analysis_jobs (assessment_id)
                """,
            )
            connection.execute(
                """
                CREATE INDEX IF NOT EXISTS
                    idx_analysis_jobs_status_created_at
                ON analysis_jobs (status, created_at)
                """,
            )

    @contextmanager
    def _connection(
        self,
    ) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(
            self._database_path,
            timeout=30,
        )
        connection.row_factory = sqlite3.Row
        connection.execute(
            "PRAGMA foreign_keys = ON",
        )

        try:
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()


def _row_to_analysis(
    row: sqlite3.Row,
) -> StoredAnalysis:
    try:
        request_body = json.loads(
            row["request_body"],
        )
        retry_items = json.loads(
            row["retry_items"],
        )
        result_body = (
            json.loads(row["result_body"])
            if row["result_body"] is not None
            else None
        )
    except json.JSONDecodeError as error:
        raise AnalysisRepositoryError(
            "저장된 분석 작업 JSON이 "
            "손상되었습니다.",
        ) from error

    if not isinstance(request_body, dict):
        raise AnalysisRepositoryError(
            "저장된 분석 요청 형식이 올바르지 않습니다.",
        )

    if (
        not isinstance(retry_items, list)
        or not all(
            isinstance(item, dict)
            for item in retry_items
        )
    ):
        raise AnalysisRepositoryError(
            "저장된 재시도 항목 형식이 "
            "올바르지 않습니다.",
        )

    if (
        result_body is not None
        and not isinstance(result_body, dict)
    ):
        raise AnalysisRepositoryError(
            "저장된 최종 결과 형식이 "
            "올바르지 않습니다.",
        )

    return StoredAnalysis(
        analysis_id=UUID(row["analysis_id"]),
        assessment_id=UUID(
            row["assessment_id"],
        ),
        status=AnalysisStatus(
            row["status"],
        ),
        request_body=request_body,
        retryable=bool(row["retryable"]),
        reason_code=row["reason_code"],
        retry_items=tuple(retry_items),
        result_body=result_body,
        created_at=datetime.fromisoformat(
            row["created_at"],
        ),
        updated_at=datetime.fromisoformat(
            row["updated_at"],
        ),
    )

def _row_to_archived_attempt(
    row: sqlite3.Row,
) -> ArchivedAnalysisAttempt:
    try:
        request_body = json.loads(
            row["request_body"],
        )
        retry_items = json.loads(
            row["retry_items"],
        )
        result_body = (
            json.loads(row["result_body"])
            if row["result_body"] is not None
            else None
        )
    except json.JSONDecodeError as error:
        raise AnalysisRepositoryError(
            "저장된 분석 시도 이력이 "
            "손상되었습니다.",
        ) from error

    if not isinstance(request_body, dict):
        raise AnalysisRepositoryError(
            "분석 시도 요청 형식이 "
            "올바르지 않습니다.",
        )

    if (
        not isinstance(retry_items, list)
        or not all(
            isinstance(item, dict)
            for item in retry_items
        )
    ):
        raise AnalysisRepositoryError(
            "분석 시도의 재시도 항목 형식이 "
            "올바르지 않습니다.",
        )

    if (
        result_body is not None
        and not isinstance(result_body, dict)
    ):
        raise AnalysisRepositoryError(
            "분석 시도의 결과 형식이 "
            "올바르지 않습니다.",
        )

    return ArchivedAnalysisAttempt(
        analysis_id=UUID(
            row["analysis_id"],
        ),
        attempt_number=int(
            row["attempt_number"],
        ),
        status=AnalysisStatus(
            row["status"],
        ),
        request_body=request_body,
        retryable=bool(
            row["retryable"],
        ),
        reason_code=row["reason_code"],
        retry_items=tuple(
            retry_items,
        ),
        result_body=result_body,
        created_at=datetime.fromisoformat(
            row["created_at"],
        ),
        updated_at=datetime.fromisoformat(
            row["updated_at"],
        ),
        archived_at=datetime.fromisoformat(
            row["archived_at"],
        ),
    )

def _serialize_json(
    value: Any,
) -> str:
    return json.dumps(
        value,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )


def _current_timestamp() -> str:
    return datetime.now(UTC).isoformat()