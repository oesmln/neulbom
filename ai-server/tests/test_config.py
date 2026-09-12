from pathlib import Path

from app.core.config import (
    PROJECT_ROOT,
    Settings,
)


def test_default_settings() -> None:
    settings = Settings(_env_file=None)

    assert settings.app_env == "local"
    assert settings.allows_local_audio_urls is True
    assert settings.log_level == "INFO"
    assert settings.contracts_dir == (
        PROJECT_ROOT / "contracts"
    ).resolve()
    assert settings.artifacts_dir == (
        PROJECT_ROOT
        / "artifacts"
        / "models"
    ).resolve()
    assert settings.service_token is None
    assert settings.allowed_audio_download_hosts == frozenset()
    assert settings.idempotency_db_path == (
        PROJECT_ROOT
        / "data"
        / "idempotency.sqlite3"
    ).resolve()
    assert settings.analysis_db_path == (
        PROJECT_ROOT
        / "data"
        / "analyses.sqlite3"
    ).resolve()
    assert (
        settings
        .analysis_processing_timeout_seconds
        == 300.0
    )


def test_environment_variables_override_defaults(
    monkeypatch,
    tmp_path: Path,
) -> None:
    artifacts_dir = tmp_path / "models"
    idempotency_db_path = (
        tmp_path / "idempotency.sqlite3"
    )
    analysis_db_path = (
        tmp_path / "analyses.sqlite3"
    )
    service_token = (
        "test-service-token-value"
    )

    monkeypatch.setenv(
        "AI_SERVER_APP_ENV",
        "test",
    )
    monkeypatch.setenv(
        "AI_SERVER_LOG_LEVEL",
        "debug",
    )
    monkeypatch.setenv(
        "AI_SERVER_CONTRACTS_DIR",
        "custom-contracts",
    )
    monkeypatch.setenv(
        "AI_SERVER_ARTIFACTS_DIR",
        str(artifacts_dir),
    )
    monkeypatch.setenv(
        "AI_SERVER_SERVICE_TOKEN",
        service_token,
    )
    monkeypatch.setenv(
        "AI_SERVER_AUDIO_DOWNLOAD_ALLOWED_HOSTS",
        "Storage.Example., cdn.example",
    )
    monkeypatch.setenv(
        "AI_SERVER_IDEMPOTENCY_DB_PATH",
        str(idempotency_db_path),
    )
    monkeypatch.setenv(
        "AI_SERVER_ANALYSIS_DB_PATH",
        str(analysis_db_path),
    )
    monkeypatch.setenv(
        "AI_SERVER_ANALYSIS_PROCESSING_TIMEOUT_SECONDS",
        "120",
    )

    settings = Settings(_env_file=None)

    assert settings.app_env == "test"
    assert settings.allows_local_audio_urls is True
    assert settings.log_level == "DEBUG"
    assert settings.contracts_dir == (
        PROJECT_ROOT / "custom-contracts"
    ).resolve()
    assert settings.artifacts_dir == (
        artifacts_dir.resolve()
    )

    assert settings.service_token is not None
    assert (
        settings.service_token.get_secret_value()
        == service_token
    )
    assert service_token not in repr(settings)
    assert settings.allowed_audio_download_hosts == frozenset(
        {"storage.example", "cdn.example"},
    )

    assert settings.idempotency_db_path == (
        idempotency_db_path.resolve()
    )
    assert settings.analysis_db_path == (
        analysis_db_path.resolve()
    )
    assert (
        settings
        .analysis_processing_timeout_seconds
        == 120.0
    )


def test_production_disables_local_audio_urls(monkeypatch) -> None:
    monkeypatch.setenv(
        "AI_SERVER_APP_ENV",
        "production",
    )

    settings = Settings(_env_file=None)

    assert settings.allows_local_audio_urls is False
