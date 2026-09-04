from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import SecretStr, field_validator
from pydantic_settings import (
    BaseSettings,
    SettingsConfigDict,
)

PROJECT_ROOT = Path(__file__).resolve().parents[2]

AppEnvironment = Literal[
    "local",
    "test",
    "development",
    "production",
]
LogLevel = Literal[
    "DEBUG",
    "INFO",
    "WARNING",
    "ERROR",
    "CRITICAL",
]


class Settings(BaseSettings):
    """환경변수와 .env 파일에서 AI 서버 설정을 읽는다."""

    model_config = SettingsConfigDict(
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        env_prefix="AI_SERVER_",
        case_sensitive=False,
        env_ignore_empty=True,
        extra="ignore",
        validate_default=True,
        frozen=True,
    )

    app_env: AppEnvironment = "local"
    log_level: LogLevel = "INFO"
    contracts_dir: Path = (
        PROJECT_ROOT / "contracts"
    )
    artifacts_dir: Path = (
        PROJECT_ROOT / "artifacts" / "models"
    )
    service_token: SecretStr | None = None
    idempotency_db_path: Path = (
        PROJECT_ROOT
        / "data"
        / "idempotency.sqlite3"
    )
    analysis_db_path: Path = (
        PROJECT_ROOT
        / "data"
        / "analyses.sqlite3"
    )
    audio_download_timeout_seconds: float = 30.0
    max_audio_download_bytes: int = (
        32 * 1024 * 1024
    )
    analysis_processing_timeout_seconds: float = (
        300.0
    )

    @field_validator(
        "log_level",
        mode="before",
    )
    @classmethod
    def normalize_log_level(
        cls,
        value: object,
    ) -> object:
        if isinstance(value, str):
            return value.upper()

        return value

    @field_validator(
        "contracts_dir",
        "artifacts_dir",
        "idempotency_db_path",
        "analysis_db_path",
        mode="after",
    )
    @classmethod
    def resolve_project_path(
        cls,
        value: Path,
    ) -> Path:
        if value.is_absolute():
            return value.resolve()

        return (
            PROJECT_ROOT / value
        ).resolve()


@lru_cache
def get_settings() -> Settings:
    """동일 프로세스에서 설정 객체를 한 번만 생성한다."""
    return Settings()