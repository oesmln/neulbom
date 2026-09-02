import hashlib
import json
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from app.api.errors import APIError
from app.repositories.idempotency import (
    IdempotencyRepository,
    ReservationOutcome,
    StoredHttpResponse,
)


@dataclass(frozen=True, slots=True)
class IdempotentExecutionResult:
    response: StoredHttpResponse
    replayed: bool


class InvalidIdempotencyKeyError(ValueError):
    """멱등 키 형식이 잘못된 경우 발생한다."""


class IdempotencyService:
    def __init__(
        self,
        repository: IdempotencyRepository,
    ) -> None:
        self._repository = repository

    def execute(
        self,
        *,
        scope: str,
        idempotency_key: str,
        request_body: Any,
        operation: Callable[
            [],
            StoredHttpResponse,
        ],
    ) -> IdempotentExecutionResult:
        normalized_scope = scope.strip()
        normalized_key = (
            _normalize_idempotency_key(
                idempotency_key,
            )
        )

        if not normalized_scope:
            raise ValueError(
                "idempotency scope는 비어 있을 수 없습니다.",
            )

        request_hash = create_request_hash(
            request_body,
        )

        reservation = self._repository.reserve(
            scope=normalized_scope,
            idempotency_key=normalized_key,
            request_hash=request_hash,
        )

        if (
            reservation.outcome
            == ReservationOutcome.CONFLICT
        ):
            raise APIError(
                status_code=409,
                code="IDEMPOTENCY_CONFLICT",
                message=(
                    "The Idempotency-Key was already "
                    "used with a different request body."
                ),
                retryable=False,
                details={},
            )

        if (
            reservation.outcome
            == ReservationOutcome.IN_PROGRESS
        ):
            raise APIError(
                status_code=409,
                code="INVALID_ANALYSIS_STATE",
                message=(
                    "An identical request is already "
                    "being processed."
                ),
                retryable=True,
                details={},
            )

        if reservation.outcome == ReservationOutcome.REPLAY:
            if reservation.response is None:
                raise RuntimeError(
                    "재사용할 멱등 응답이 없습니다.",
                )

            return IdempotentExecutionResult(
                response=reservation.response,
                replayed=True,
            )

        try:
            response = operation()
        except Exception:
            self._repository.abandon(
                scope=normalized_scope,
                idempotency_key=normalized_key,
                request_hash=request_hash,
            )
            raise

        self._repository.complete(
            scope=normalized_scope,
            idempotency_key=normalized_key,
            request_hash=request_hash,
            response=response,
        )

        return IdempotentExecutionResult(
            response=response,
            replayed=False,
        )


def create_request_hash(
    request_body: Any,
) -> str:
    """JSON 키 순서와 무관한 요청 본문 해시를 생성한다."""
    try:
        canonical_json = json.dumps(
            request_body,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        )
    except (TypeError, ValueError) as error:
        raise ValueError(
            "요청 본문은 유효한 JSON 값이어야 합니다.",
        ) from error

    return hashlib.sha256(
        canonical_json.encode("utf-8"),
    ).hexdigest()


def _normalize_idempotency_key(
    idempotency_key: str,
) -> str:
    normalized_key = idempotency_key.strip()

    if not 16 <= len(normalized_key) <= 200:
        raise InvalidIdempotencyKeyError(
            "Idempotency-Key는 16자 이상 "
            "200자 이하여야 합니다.",
        )

    return normalized_key