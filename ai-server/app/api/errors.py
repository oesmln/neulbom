from typing import Any, Literal

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel

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
    response = ErrorResponse(
        error=ErrorObject(
            code=error.code,
            message=error.message,
            retryable=error.retryable,
            details=error.details,
        ),
    )

    return JSONResponse(
        status_code=error.status_code,
        content=response.model_dump(mode="json"),
        headers=error.headers,
    )


def register_exception_handlers(
    app: FastAPI,
) -> None:
    """AI 서버 공통 예외 처리기를 등록한다."""
    app.add_exception_handler(
        APIError,
        api_error_handler,
    )