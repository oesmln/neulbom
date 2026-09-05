import logging
from collections.abc import Sequence
from typing import Any, Literal

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel

logger = logging.getLogger(__name__)

SUPPORTED_QUESTION_SET_VERSION = "cist-v1"
SUPPORTED_WRONG_EVENT_RULE_VERSION = "wrong-event-v1"

ErrorCode = Literal[
    "UNAUTHORIZED",
    "ANALYSIS_NOT_FOUND",
    "VALIDATION_ERROR",
    "QUESTION_SET_MISMATCH",
    "WRONG_EVENT_RULE_MISMATCH",
    "IDEMPOTENCY_CONFLICT",
    "INVALID_ANALYSIS_STATE",
    "MODEL_UNAVAILABLE",
    "INTERNAL_ERROR",
]


class ErrorObject(BaseModel):
    code: ErrorCode
    message: str
    retryable: bool
    details: dict[str, Any]


class ErrorResponse(BaseModel):
    error: ErrorObject


class APIError(Exception):
    """OpenAPI 공통 오류 응답으로 변환할 예외."""

    def __init__(
        self,
        *,
        status_code: int,
        code: ErrorCode,
        message: str,
        retryable: bool,
        details: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
    ) -> None:
        super().__init__(message)

        self.status_code = status_code
        self.code = code
        self.message = message
        self.retryable = retryable
        self.details = details or {}
        self.headers = headers


async def api_error_handler(
    _request: Request,
    error: APIError,
) -> JSONResponse:
    return _create_error_response(
        status_code=error.status_code,
        code=error.code,
        message=error.message,
        retryable=error.retryable,
        details=error.details,
        headers=error.headers,
    )


async def request_validation_error_handler(
    _request: Request,
    error: RequestValidationError,
) -> JSONResponse:
    errors = error.errors()

    question_set_version = _find_invalid_version(
        errors,
        "question_set_version",
    )
    if question_set_version is not None:
        return _create_error_response(
            status_code=409,
            code="QUESTION_SET_MISMATCH",
            message="Unsupported question_set_version.",
            retryable=False,
            details={
                "received_version": question_set_version,
                "supported_version": (
                    SUPPORTED_QUESTION_SET_VERSION
                ),
            },
        )

    wrong_event_rule_version = _find_invalid_version(
        errors,
        "wrong_event_rule_version",
    )
    if wrong_event_rule_version is not None:
        return _create_error_response(
            status_code=409,
            code="WRONG_EVENT_RULE_MISMATCH",
            message=(
                "Unsupported wrong_event_rule_version."
            ),
            retryable=False,
            details={
                "received_version": (
                    wrong_event_rule_version
                ),
                "supported_version": (
                    SUPPORTED_WRONG_EVENT_RULE_VERSION
                ),
            },
        )

    fields = sorted(
        {
            _format_validation_location(
                validation_error.get("loc", ()),
            )
            for validation_error in errors
        },
    )

    return _create_error_response(
        status_code=422,
        code="VALIDATION_ERROR",
        message="Request validation failed.",
        retryable=False,
        details={
            # 입력값이나 STT 원문은 응답에 포함하지 않는다.
            "fields": fields,
        },
    )


async def unexpected_error_handler(
    _request: Request,
    error: Exception,
) -> JSONResponse:
    # 구체적인 오류는 로그에만 남기고 응답에는 노출하지 않는다.
    logger.error(
        "Unhandled application error",
        exc_info=(
            type(error),
            error,
            error.__traceback__,
        ),
    )

    return _create_error_response(
        status_code=500,
        code="INTERNAL_ERROR",
        message=(
            "An unexpected internal error occurred."
        ),
        retryable=True,
        details={},
    )


def model_unavailable_error(
    *,
    retry_after_seconds: int = 5,
) -> APIError:
    """필수 모델을 사용할 수 없을 때 사용할 오류."""
    retry_after = max(
        1,
        retry_after_seconds,
    )

    return APIError(
        status_code=503,
        code="MODEL_UNAVAILABLE",
        message=(
            "A required model is temporarily unavailable."
        ),
        retryable=True,
        details={},
        headers={
            "Retry-After": str(retry_after),
        },
    )


def register_exception_handlers(
    app: FastAPI,
) -> None:
    """AI 서버 공통 예외 처리기를 등록한다."""
    app.add_exception_handler(
        APIError,
        api_error_handler,
    )
    app.add_exception_handler(
        RequestValidationError,
        request_validation_error_handler,
    )
    app.add_exception_handler(
        Exception,
        unexpected_error_handler,
    )


def _create_error_response(
    *,
    status_code: int,
    code: ErrorCode,
    message: str,
    retryable: bool,
    details: dict[str, Any],
    headers: dict[str, str] | None = None,
) -> JSONResponse:
    response = ErrorResponse(
        error=ErrorObject(
            code=code,
            message=message,
            retryable=retryable,
            details=details,
        ),
    )

    return JSONResponse(
        status_code=status_code,
        content=response.model_dump(mode="json"),
        headers=headers,
    )


def _find_invalid_version(
    errors: list[dict[str, Any]],
    field_name: str,
) -> str | None:
    for validation_error in errors:
        location = validation_error.get(
            "loc",
            (),
        )

        if (
            location
            and location[-1] == field_name
            and validation_error.get("type")
            == "literal_error"
        ):
            received_value = validation_error.get(
                "input",
            )

            if isinstance(received_value, str):
                return received_value

    return None


def _format_validation_location(
    location: Sequence[str | int],
) -> str:
    parts: list[str] = []

    for location_part in location:
        # JSON 본문이라는 정보는 필드 경로에서 생략한다.
        if (
            location_part == "body"
            and not parts
        ):
            continue

        if isinstance(location_part, int):
            if parts:
                parts[-1] += f"[{location_part}]"
            else:
                parts.append(
                    f"[{location_part}]",
                )
            continue

        if parts:
            parts.append(
                f".{location_part}",
            )
        else:
            parts.append(location_part)

    return "".join(parts) or "request"