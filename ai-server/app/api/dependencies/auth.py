from secrets import compare_digest
from typing import Annotated

from fastapi import Depends
from fastapi.security import (
    HTTPAuthorizationCredentials,
    HTTPBearer,
)

from app.api.errors import APIError
from app.core.config import Settings, get_settings

bearer_scheme = HTTPBearer(
    scheme_name="bearerAuth",
    description=(
        "백엔드와 AI 서버 사이에서 사용하는 "
        "서비스 간 Bearer Token"
    ),
    auto_error=False,
)


def require_service_token(
    credentials: Annotated[
        HTTPAuthorizationCredentials | None,
        Depends(bearer_scheme),
    ],
    settings: Annotated[
        Settings,
        Depends(get_settings),
    ],
) -> None:
    """요청의 Bearer Token을 서버 설정값과 비교한다."""
    expected_secret = settings.service_token

    if (
        credentials is None
        or credentials.scheme.lower() != "bearer"
        or expected_secret is None
    ):
        _raise_unauthorized()

    expected_token = expected_secret.get_secret_value()
    supplied_token = credentials.credentials

    if not compare_digest(
        supplied_token,
        expected_token,
    ):
        _raise_unauthorized()


def _raise_unauthorized() -> None:
    raise APIError(
        status_code=401,
        code="UNAUTHORIZED",
        message="Missing or invalid AI service token.",
        retryable=False,
        details={},
        headers={
            "WWW-Authenticate": "Bearer",
        },
    )