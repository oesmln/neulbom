import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass

from fastapi import FastAPI

from app.contracts.loader import (
    ContractLoadError,
    load_contract_bundle,
)
from app.contracts.models import ContractBundle
from app.contracts.validator import ContractValidationError
from app.core.config import get_settings

logger = logging.getLogger(__name__)


@dataclass(slots=True)
class RuntimeState:
    """AI 서버 실행 중 공유하는 준비 상태."""

    contract_bundle: ContractBundle | None = None
    contract_error: str | None = None

    @property
    def is_ready(self) -> bool:
        return (
            self.contract_bundle is not None
            and self.contract_error is None
        )


@asynccontextmanager
async def lifespan(
    app: FastAPI,
) -> AsyncIterator[None]:
    """서버 시작과 종료 시 필요한 자원을 관리한다."""
    runtime_state = RuntimeState()
    app.state.runtime_state = runtime_state

    settings = get_settings()

    try:
        runtime_state.contract_bundle = (
            load_contract_bundle(
                settings.contracts_dir,
            )
        )
    except (
        ContractLoadError,
        ContractValidationError,
    ) as error:
        runtime_state.contract_error = str(error)
        logger.exception(
            "기준 계약 파일을 로딩하지 못했습니다.",
        )

    try:
        yield
    finally:
        runtime_state.contract_bundle = None