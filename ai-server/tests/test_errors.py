from typing import Literal

from fastapi import FastAPI
from fastapi.testclient import TestClient
from pydantic import BaseModel, ConfigDict

from app.api.errors import (
    APIError,
    model_unavailable_error,
    register_exception_handlers,
)


class VersionedRequest(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
    )

    question_set_version: Literal["cist-v1"]
    wrong_event_rule_version: Literal[
        "wrong-event-v1"
    ]
    value: int


def test_converts_validation_error_to_error_response() -> None:
    client = _create_test_client()

    response = client.post(
        "/validated",
        json={
            "question_set_version": "cist-v1",
            "wrong_event_rule_version": (
                "wrong-event-v1"
            ),
        },
    )

    assert response.status_code == 422
    assert response.json() == {
        "error": {
            "code": "VALIDATION_ERROR",
            "message": "Request validation failed.",
            "retryable": False,
            "details": {
                "fields": ["value"],
            },
        },
    }


def test_rejects_unknown_request_field() -> None:
    client = _create_test_client()

    response = client.post(
        "/validated",
        json={
            "question_set_version": "cist-v1",
            "wrong_event_rule_version": (
                "wrong-event-v1"
            ),
            "value": 1,
            "unknown": "not-allowed",
        },
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == (
        "VALIDATION_ERROR"
    )
    assert response.json()["error"]["details"][
        "fields"
    ] == ["unknown"]


def test_maps_question_set_mismatch_to_409() -> None:
    client = _create_test_client()

    response = client.post(
        "/validated",
        json={
            "question_set_version": "cist-v0",
            "wrong_event_rule_version": (
                "wrong-event-v1"
            ),
            "value": 1,
        },
    )

    assert response.status_code == 409
    assert response.json() == {
        "error": {
            "code": "QUESTION_SET_MISMATCH",
            "message": (
                "Unsupported question_set_version."
            ),
            "retryable": False,
            "details": {
                "received_version": "cist-v0",
                "supported_version": "cist-v1",
            },
        },
    }


def test_maps_wrong_event_rule_mismatch_to_409() -> None:
    client = _create_test_client()

    response = client.post(
        "/validated",
        json={
            "question_set_version": "cist-v1",
            "wrong_event_rule_version": (
                "wrong-event-v0"
            ),
            "value": 1,
        },
    )

    assert response.status_code == 409
    assert response.json()["error"] == {
        "code": "WRONG_EVENT_RULE_MISMATCH",
        "message": (
            "Unsupported wrong_event_rule_version."
        ),
        "retryable": False,
        "details": {
            "received_version": "wrong-event-v0",
            "supported_version": "wrong-event-v1",
        },
    }


def test_returns_model_unavailable_error() -> None:
    client = _create_test_client()

    response = client.get("/model-unavailable")

    assert response.status_code == 503
    assert response.headers["retry-after"] == "7"
    assert response.json() == {
        "error": {
            "code": "MODEL_UNAVAILABLE",
            "message": (
                "A required model is temporarily "
                "unavailable."
            ),
            "retryable": True,
            "details": {},
        },
    }


def test_hides_unexpected_internal_error() -> None:
    client = _create_test_client(
        raise_server_exceptions=False,
    )

    response = client.get("/unexpected")

    assert response.status_code == 500
    assert response.json() == {
        "error": {
            "code": "INTERNAL_ERROR",
            "message": (
                "An unexpected internal error occurred."
            ),
            "retryable": True,
            "details": {},
        },
    }

    response_text = response.text
    assert "database-password" not in response_text
    assert "RuntimeError" not in response_text


def test_preserves_explicit_api_error() -> None:
    client = _create_test_client()

    response = client.get("/conflict")

    assert response.status_code == 409
    assert response.json()["error"]["code"] == (
        "INVALID_ANALYSIS_STATE"
    )


def _create_test_client(
    *,
    raise_server_exceptions: bool = True,
) -> TestClient:
    app = FastAPI()
    register_exception_handlers(app)

    @app.post("/validated")
    def validated(
        request: VersionedRequest,
    ) -> dict[str, int]:
        return {
            "value": request.value,
        }

    @app.get("/model-unavailable")
    def model_unavailable() -> None:
        raise model_unavailable_error(
            retry_after_seconds=7,
        )

    @app.get("/unexpected")
    def unexpected() -> None:
        raise RuntimeError(
            "database-password must not be exposed",
        )

    @app.get("/conflict")
    def conflict() -> None:
        raise APIError(
            status_code=409,
            code="INVALID_ANALYSIS_STATE",
            message="Invalid analysis state.",
            retryable=False,
            details={},
        )

    return TestClient(
        app,
        raise_server_exceptions=(
            raise_server_exceptions
        ),
    )