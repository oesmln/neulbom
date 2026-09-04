from typing import Literal

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from app.core.runtime import RuntimeState

router = APIRouter(
    prefix="/health",
    tags=["health"],
)


class LivenessResponse(BaseModel):
    status: Literal["ok"]


class ReadinessResponse(BaseModel):
    status: Literal["ok", "not_ready"]
    reason: Literal[
        "CONTRACTS_UNAVAILABLE",
        "ANALYSIS_RUNTIME_UNAVAILABLE",
    ] | None = None


@router.get(
    "/live",
    response_model=LivenessResponse,
)
def liveness() -> LivenessResponse:
    """서버 프로세스가 요청을 처리할 수 있는지 확인한다."""
    return LivenessResponse(status="ok")


@router.get(
    "/ready",
    response_model=ReadinessResponse,
    response_model_exclude_none=True,
    responses={
        503: {
            "model": ReadinessResponse,
            "description": (
                "AI 서버가 분석 요청을 "
                "받을 준비가 되지 않음"
            ),
        },
    },
)
def readiness(
    request: Request,
) -> ReadinessResponse | JSONResponse:
    runtime_state: RuntimeState = (
        request.app.state.runtime_state
    )

    if runtime_state.is_ready:
        return ReadinessResponse(
            status="ok",
        )

    reason = (
        "CONTRACTS_UNAVAILABLE"
        if runtime_state.contract_bundle
        is None
        else "ANALYSIS_RUNTIME_UNAVAILABLE"
    )

    return JSONResponse(
        status_code=503,
        content={
            "status": "not_ready",
            "reason": reason,
        },
    )