import json
import logging

from app.core.logging import (
    JsonFormatter,
    RequestContextFilter,
    redact_sensitive_text,
    reset_request_id,
    set_request_id,
)


def test_redacts_sensitive_values() -> None:
    message = (
        "Authorization: Bearer secret-token "
        'raw_transcript="민수는 공원에 갔어요" '
        "signed_url=https://storage.example/"
        "audio.m4a?signature=secret-signature "
        "service_token=server-secret"
    )

    redacted = redact_sensitive_text(message)

    assert "secret-token" not in redacted
    assert "민수는 공원에 갔어요" not in redacted
    assert "secret-signature" not in redacted
    assert "server-secret" not in redacted
    assert "[REDACTED]" in redacted


def test_json_formatter_includes_request_context() -> None:
    context_token = set_request_id(
        "test-request-id",
    )

    try:
        record = logging.LogRecord(
            name="test.logger",
            level=logging.INFO,
            pathname=__file__,
            lineno=1,
            msg="request completed",
            args=(),
            exc_info=None,
        )
        record.http_method = "GET"
        record.http_path = "/health/live"
        record.status_code = 200
        record.duration_ms = 1.25

        context_filter = RequestContextFilter()
        context_filter.filter(record)

        formatted = JsonFormatter().format(record)
        payload = json.loads(formatted)

        assert payload["level"] == "INFO"
        assert payload["logger"] == "test.logger"
        assert (
            payload["request_id"]
            == "test-request-id"
        )
        assert payload["http_method"] == "GET"
        assert payload["http_path"] == (
            "/health/live"
        )
        assert payload["status_code"] == 200
        assert payload["duration_ms"] == 1.25
    finally:
        reset_request_id(context_token)