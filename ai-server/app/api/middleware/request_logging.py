import logging
import re
from time import perf_counter
from uuid import uuid4

from fastapi import Request, Response
from starlette.middleware.base import (
    BaseHTTPMiddleware,
    RequestResponseEndpoint,
)

from app.core.logging import (
    reset_request_id,
    set_request_id,
)

logger = logging.getLogger(__name__)

REQUEST_ID_HEADER = "X-Request-ID"

_REQUEST_ID_PATTERN = re.compile(
    r"^[A-Za-z0-9._-]{1,64}$",
)


class RequestLoggingMiddleware(
    BaseHTTPMiddleware,
):
    """요청 ID와 처리 시간을 기록한다."""

    async def dispatch(
        self,
        request: Request,
        call_next: RequestResponseEndpoint,
    ) -> Response:
        request_id = _resolve_request_id(request)
        context_token = set_request_id(request_id)
        started_at = perf_counter()

        try:
            response = await call_next(request)

            duration_ms = round(
                (perf_counter() - started_at) * 1000,
                2,
            )

            response.headers[
                REQUEST_ID_HEADER
            ] = request_id

            logger.info(
                "HTTP request completed",
                extra={
                    "http_method": request.method,
                    # query string은 기록하지 않는다.
                    "http_path": request.url.path,
                    "status_code": (
                        response.status_code
                    ),
                    "duration_ms": duration_ms,
                },
            )

            return response
        except Exception:
            duration_ms = round(
                (perf_counter() - started_at) * 1000,
                2,
            )

            logger.exception(
                "HTTP request failed",
                extra={
                    "http_method": request.method,
                    "http_path": request.url.path,
                    "status_code": 500,
                    "duration_ms": duration_ms,
                },
            )
            raise
        finally:
            reset_request_id(context_token)


def _resolve_request_id(
    request: Request,
) -> str:
    received_request_id = request.headers.get(
        REQUEST_ID_HEADER,
    )

    if (
        received_request_id
        and _REQUEST_ID_PATTERN.fullmatch(
            received_request_id,
        )
    ):
        return received_request_id

    return uuid4().hex