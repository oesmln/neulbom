from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter(prefix="/health", tags=["health"])


class HealthResponse(BaseModel):
    status: Literal["ok"]


@router.get("/live", response_model=HealthResponse)
def liveness() -> HealthResponse:
    """프로세스가 요청을 처리할 수 있는지 확인한다."""
    return HealthResponse(status="ok")


@router.get("/ready", response_model=HealthResponse)
def readiness() -> HealthResponse:
    """서비스가 요청을 받을 준비가 되었는지 확인한다."""
    return HealthResponse(status="ok")
