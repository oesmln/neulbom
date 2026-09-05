from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from app.api.dependencies.auth import (
    require_service_token,
)
from app.api.errors import register_exception_handlers
from app.core.config import Settings, get_settings

VALID_SERVICE_TOKEN = (
    "test-service-token-with-at-least-32-characters"
)


def test_accepts_valid_bearer_token() -> None:
    client = _create_test_client(
        configured_token=VALID_SERVICE_TOKEN,
    )

    response = client.get(
        "/protected",
        headers={
            "Authorization": (
                f"Bearer {VALID_SERVICE_TOKEN}"
            ),
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
    }


def test_rejects_missing_authorization_header() -> None:
    client = _create_test_client(
        configured_token=VALID_SERVICE_TOKEN,
    )

    response = client.get("/protected")

    assert response.status_code == 401
    assert response.json() == {
        "error": {
            "code": "UNAUTHORIZED",
            "message": (
                "Missing or invalid AI service token."
            ),
            "retryable": False,
            "details": {},
        },
    }
    assert response.headers["www-authenticate"] == "Bearer"


def test_rejects_invalid_bearer_token() -> None:
    client = _create_test_client(
        configured_token=VALID_SERVICE_TOKEN,
    )

    response = client.get(
        "/protected",
        headers={
            "Authorization": "Bearer invalid-token",
        },
    )

    assert response.status_code == 401
    assert response.json()["error"]["code"] == (
        "UNAUTHORIZED"
    )


def test_rejects_request_when_server_token_is_missing() -> None:
    client = _create_test_client(
        configured_token=None,
    )

    response = client.get(
        "/protected",
        headers={
            "Authorization": (
                f"Bearer {VALID_SERVICE_TOKEN}"
            ),
        },
    )

    assert response.status_code == 401
    assert response.json()["error"]["code"] == (
        "UNAUTHORIZED"
    )


def _create_test_client(
    configured_token: str | None,
) -> TestClient:
    app = FastAPI()
    register_exception_handlers(app)

    settings = Settings(
        _env_file=None,
        service_token=configured_token,
    )

    app.dependency_overrides[get_settings] = (
        lambda: settings
    )

    @app.get(
        "/protected",
        dependencies=[
            Depends(require_service_token),
        ],
    )
    def protected_route() -> dict[str, str]:
        return {
            "status": "ok",
        }

    return TestClient(app)