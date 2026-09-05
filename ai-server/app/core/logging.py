import json
import logging
import re
import sys
from contextvars import ContextVar, Token
from datetime import UTC, datetime
from typing import Any

_request_id_context: ContextVar[str] = ContextVar(
    "request_id",
    default="-",
)

_BEARER_TOKEN_PATTERN = re.compile(
    r"(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]+",
)

_QUERY_SECRET_PATTERN = re.compile(
    (
        r"(?i)"
        r"([?&](?:signature|x-goog-signature|"
        r"x-amz-signature|token|access_token)=)"
        r"[^&\s]+"
    ),
)

_SENSITIVE_FIELD_PATTERN = re.compile(
    (
        r"(?i)"
        r"([\"']?(?:service_token|raw_transcript|"
        r"signed_url)[\"']?\s*[:=]\s*)"
        r"(?:\"[^\"]*\"|'[^']*'|[^\s,}]+)"
    ),
)


def set_request_id(
    request_id: str,
) -> Token[str]:
    """현재 요청의 request ID를 context에 저장한다."""
    return _request_id_context.set(request_id)


def reset_request_id(
    token: Token[str],
) -> None:
    """요청 처리가 끝나면 기존 context로 복구한다."""
    _request_id_context.reset(token)


def get_request_id() -> str:
    return _request_id_context.get()


def redact_sensitive_text(
    value: str,
) -> str:
    """로그 문자열에서 민감한 값을 제거한다."""
    redacted = _SENSITIVE_FIELD_PATTERN.sub(
        r"\1[REDACTED]",
        value,
    )
    redacted = _BEARER_TOKEN_PATTERN.sub(
        r"\1[REDACTED]",
        redacted,
    )
    redacted = _QUERY_SECRET_PATTERN.sub(
        r"\1[REDACTED]",
        redacted,
    )

    return redacted


class RequestContextFilter(logging.Filter):
    """모든 로그 레코드에 request ID를 추가한다."""

    def filter(
        self,
        record: logging.LogRecord,
    ) -> bool:
        record.request_id = get_request_id()
        return True


class JsonFormatter(logging.Formatter):
    """표준 로그 레코드를 JSON 한 줄로 변환한다."""

    def format(
        self,
        record: logging.LogRecord,
    ) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.fromtimestamp(
                record.created,
                tz=UTC,
            ).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "request_id": getattr(
                record,
                "request_id",
                "-",
            ),
            "message": redact_sensitive_text(
                record.getMessage(),
            ),
        }

        for field_name in (
            "http_method",
            "http_path",
            "status_code",
            "duration_ms",
        ):
            if hasattr(record, field_name):
                payload[field_name] = getattr(
                    record,
                    field_name,
                )

        if record.exc_info:
            exception_text = self.formatException(
                record.exc_info,
            )
            payload["exception"] = (
                redact_sensitive_text(
                    exception_text,
                )
            )

        return json.dumps(
            payload,
            ensure_ascii=False,
            separators=(",", ":"),
        )


def configure_logging(
    log_level: str,
) -> None:
    """AI 서버의 root logger를 구성한다."""
    level = getattr(
        logging,
        log_level.upper(),
        logging.INFO,
    )

    handler = logging.StreamHandler(sys.stdout)
    handler.setLevel(level)
    handler.setFormatter(JsonFormatter())
    handler.addFilter(RequestContextFilter())

    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.setLevel(level)
    root_logger.addHandler(handler)

    # HTTP client 내부 디버그 로그에는 URL이 포함될 수 있다.
    logging.getLogger("httpx").setLevel(
        logging.WARNING,
    )
    logging.getLogger("httpcore").setLevel(
        logging.WARNING,
    )