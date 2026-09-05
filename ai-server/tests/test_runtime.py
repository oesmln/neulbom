from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import app.core.runtime as runtime_module
from app.core.config import Settings
from app.core.runtime import RuntimeState
from app.inference.artifacts import (
    ArtifactValidationError,
)
from app.main import create_app


@pytest.fixture(autouse=True)
def stub_model_artifact_preflight(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        runtime_module,
        "discover_model_artifacts",
        lambda _artifacts_dir: None,
    )


def test_readiness_returns_503_when_artifacts_are_invalid(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    settings = Settings(
        _env_file=None,
        artifacts_dir=(
            tmp_path / "missing-models"
        ),
        analysis_db_path=(
            tmp_path / "analyses.sqlite3"
        ),
        idempotency_db_path=(
            tmp_path / "idempotency.sqlite3"
        ),
    )

    def fail_preflight(
        _artifacts_dir: Path,
    ) -> None:
        raise ArtifactValidationError(
            "missing model artifacts",
        )

    monkeypatch.setattr(
        runtime_module,
        "get_settings",
        lambda: settings,
    )
    monkeypatch.setattr(
        runtime_module,
        "discover_model_artifacts",
        fail_preflight,
    )

    app = create_app()

    with TestClient(app) as client:
        readiness_response = client.get(
            "/health/ready",
        )
        liveness_response = client.get(
            "/health/live",
        )

        runtime_state: RuntimeState = (
            app.state.runtime_state
        )

        assert readiness_response.status_code == 503
        assert readiness_response.json() == {
            "status": "not_ready",
            "reason": (
                "MODEL_ARTIFACTS_UNAVAILABLE"
            ),
        }

        assert liveness_response.status_code == 200
        assert liveness_response.json() == {
            "status": "ok",
        }

        assert runtime_state.is_ready is False
        assert (
            runtime_state.contract_bundle
            is not None
        )
        assert runtime_state.artifact_error == (
            "missing model artifacts"
        )
        assert (
            runtime_state.analysis_repository
            is None
        )
        assert (
            runtime_state.analysis_worker
            is None
        )


def test_contract_bundle_is_loaded_on_startup(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
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

    with TestClient(app):
        runtime_state: RuntimeState = (
            app.state.runtime_state
        )

        assert runtime_state.is_ready is True
        assert runtime_state.contract_bundle is not None
        assert runtime_state.contract_error is None
        assert (
            runtime_state
            .contract_bundle
            .cist
            .question_set_version
            == "cist-v1"
        )


def test_readiness_returns_503_when_contracts_are_missing(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    missing_contracts_settings = Settings(
        _env_file=None,
        contracts_dir=tmp_path,
    )

    monkeypatch.setattr(
        runtime_module,
        "get_settings",
        lambda: missing_contracts_settings,
    )

    app = create_app()

    with TestClient(app) as client:
        readiness_response = client.get(
            "/health/ready",
        )
        liveness_response = client.get(
            "/health/live",
        )

        runtime_state: RuntimeState = (
            app.state.runtime_state
        )

        assert readiness_response.status_code == 503
        assert readiness_response.json() == {
            "status": "not_ready",
            "reason": "CONTRACTS_UNAVAILABLE",
        }

        assert liveness_response.status_code == 200
        assert liveness_response.json() == {
            "status": "ok",
        }

        assert runtime_state.is_ready is False
        assert runtime_state.contract_bundle is None
        assert runtime_state.contract_error is not None

def test_runtime_applies_analysis_processing_timeout(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    settings = Settings(
        _env_file=None,
        analysis_db_path=(
            tmp_path / "analyses.sqlite3"
        ),
        idempotency_db_path=(
            tmp_path / "idempotency.sqlite3"
        ),
        analysis_processing_timeout_seconds=(
            123.0
        ),
    )

    monkeypatch.setattr(
        runtime_module,
        "get_settings",
        lambda: settings,
    )

    app = create_app()

    with TestClient(app):
        runtime_state: RuntimeState = (
            app.state.runtime_state
        )
        worker = (
            runtime_state.analysis_worker
        )

        assert worker is not None
        assert (
            worker.processing_timeout_seconds
            == 123.0
        )