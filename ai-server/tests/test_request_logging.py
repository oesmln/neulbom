from uuid import UUID

from fastapi.testclient import TestClient

from app.main import create_app


def test_preserves_valid_request_id() -> None:
    app = create_app()

    with TestClient(app) as client:
        response = client.get(
            "/health/live",
            headers={
                "X-Request-ID": (
                    "backend-request-123"
                ),
            },
        )

    assert response.status_code == 200
    assert response.headers["x-request-id"] == (
        "backend-request-123"
    )


def test_generates_request_id_when_missing() -> None:
    app = create_app()

    with TestClient(app) as client:
        response = client.get("/health/live")

    generated_request_id = response.headers[
        "x-request-id"
    ]

    assert response.status_code == 200
    assert len(generated_request_id) == 32
    UUID(generated_request_id)


def test_replaces_invalid_request_id() -> None:
    app = create_app()

    with TestClient(app) as client:
        response = client.get(
            "/health/live",
            headers={
                "X-Request-ID": (
                    "invalid request id\n"
                ),
            },
        )

    generated_request_id = response.headers[
        "x-request-id"
    ]

    assert response.status_code == 200
    assert generated_request_id != (
        "invalid request id\n"
    )
    UUID(generated_request_id)