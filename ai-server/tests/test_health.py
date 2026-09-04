from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import app.core.runtime as runtime_module
from app.core.config import Settings
from app.main import create_app


@pytest.fixture
def client(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> Iterator[TestClient]:
    settings = Settings(
        _env_file=None,
        analysis_db_path=(
            tmp_path / "analyses.sqlite3"
        ),
        idempotency_db_path=(
            tmp_path / "idempotency.sqlite3"
        ),
    )

    monkeypatch.setattr(
        runtime_module,
        "get_settings",
        lambda: settings,
    )

    app = create_app()

    with TestClient(app) as test_client:
        yield test_client


def test_liveness(
    client: TestClient,
) -> None:
    response = client.get("/health/live")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
    }


def test_readiness(
    client: TestClient,
) -> None:
    response = client.get("/health/ready")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
    }