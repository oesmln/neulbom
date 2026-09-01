from pathlib import Path

from app.core.config import PROJECT_ROOT, Settings


def test_default_settings() -> None:
    settings = Settings(_env_file=None)

    assert settings.app_env == "local"
    assert settings.log_level == "INFO"
    assert settings.contracts_dir == (PROJECT_ROOT / "contracts").resolve()
    assert settings.artifacts_dir == (
        PROJECT_ROOT / "artifacts" / "models"
    ).resolve()


def test_environment_variables_override_defaults(
    monkeypatch,
    tmp_path: Path,
) -> None:
    artifacts_dir = tmp_path / "models"

    monkeypatch.setenv("AI_SERVER_APP_ENV", "test")
    monkeypatch.setenv("AI_SERVER_LOG_LEVEL", "debug")
    monkeypatch.setenv(
        "AI_SERVER_CONTRACTS_DIR",
        "custom-contracts",
    )
    monkeypatch.setenv(
        "AI_SERVER_ARTIFACTS_DIR",
        str(artifacts_dir),
    )

    settings = Settings(_env_file=None)

    assert settings.app_env == "test"
    assert settings.log_level == "DEBUG"
    assert settings.contracts_dir == (
        PROJECT_ROOT / "custom-contracts"
    ).resolve()
    assert settings.artifacts_dir == artifacts_dir.resolve()