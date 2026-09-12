from pathlib import Path

import yaml

from app.core.config import PROJECT_ROOT


def test_dockerfile_uses_non_root_single_worker() -> None:
    dockerfile = (
        PROJECT_ROOT / "Dockerfile"
    ).read_text(
        encoding="utf-8",
    )

    assert (
        "FROM python:3.12-slim-bookworm"
        in dockerfile
    )
    assert "USER appuser" in dockerfile
    assert '"--workers",' in dockerfile
    assert '"1"' in dockerfile
    assert "/health/ready" in dockerfile
    assert "COPY . ." not in dockerfile


def test_dockerignore_excludes_sensitive_data() -> None:
    ignored_patterns = set(
        (
            PROJECT_ROOT / ".dockerignore"
        )
        .read_text(
            encoding="utf-8",
        )
        .splitlines(),
    )

    required_patterns = {
        ".env",
        ".env.*",
        "data/",
        "models/",
        "artifacts/models/",
        "*.wav",
        "*.mp3",
        "*.m4a",
        "*.safetensors",
        "*.joblib",
    }

    assert required_patterns <= (
        ignored_patterns
    )


def test_compose_mounts_models_read_only() -> None:
    compose = yaml.safe_load(
        (
            PROJECT_ROOT / "compose.yaml"
        ).read_text(
            encoding="utf-8",
        ),
    )
    service = compose["services"][
        "ai-server"
    ]

    model_mount = next(
        mount
        for mount in service["volumes"]
        if (
            mount["target"]
            == "/app/artifacts/models"
        )
    )

    assert model_mount["type"] == "bind"
    assert model_mount["read_only"] is True
    assert (
        "AI_SERVER_MODEL_ARTIFACTS_HOST_PATH"
        in model_mount["source"]
    )


def test_compose_persists_sqlite_data() -> None:
    compose = yaml.safe_load(
        (
            PROJECT_ROOT / "compose.yaml"
        ).read_text(
            encoding="utf-8",
        ),
    )
    service = compose["services"][
        "ai-server"
    ]

    data_mount = next(
        mount
        for mount in service["volumes"]
        if mount["target"] == "/app/data"
    )

    assert data_mount == {
        "type": "volume",
        "source": "ai-server-data",
        "target": "/app/data",
    }
    assert "ai-server-data" in (
        compose["volumes"]
    )


def test_compose_uses_container_paths() -> None:
    compose = yaml.safe_load(
        (
            PROJECT_ROOT / "compose.yaml"
        ).read_text(
            encoding="utf-8",
        ),
    )
    environment = compose[
        "services"
    ]["ai-server"]["environment"]

    assert environment[
        "AI_SERVER_CONTRACTS_DIR"
    ] == "/app/contracts"
    assert environment[
        "AI_SERVER_ARTIFACTS_DIR"
    ] == "/app/artifacts/models"
    assert environment[
        "AI_SERVER_ANALYSIS_DB_PATH"
    ] == "/app/data/analyses.sqlite3"
    assert environment[
        "AI_SERVER_IDEMPOTENCY_DB_PATH"
    ] == "/app/data/idempotency.sqlite3"
    assert environment[
        "AI_SERVER_APP_ENV"
    ] == "${AI_SERVER_APP_ENV:?AI_SERVER_APP_ENV must be set}"
    assert (
        "AI_SERVER_AUDIO_DOWNLOAD_ALLOWED_HOSTS"
        in environment
    )
