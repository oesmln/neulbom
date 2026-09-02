from fastapi import FastAPI

from app.api.errors import register_exception_handlers
from app.api.middleware.request_logging import (
    RequestLoggingMiddleware,
)
from app.api.routes.health import router as health_router
from app.api.routes.recognition_plan import (
    router as recognition_plan_router,
)
from app.core.config import get_settings
from app.core.logging import configure_logging
from app.core.runtime import RuntimeState, lifespan


def create_app() -> FastAPI:
    """FastAPI 애플리케이션을 생성한다."""
    settings = get_settings()
    configure_logging(settings.log_level)

    application = FastAPI(
        title="늘봄 AI 서버",
        version="0.1.0",
        description="음성·텍스트 기반 AI 분석 API",
        lifespan=lifespan,
    )

    application.state.runtime_state = RuntimeState()

    register_exception_handlers(application)

    application.add_middleware(
        RequestLoggingMiddleware,
    )
    application.include_router(health_router)
    application.include_router(
        recognition_plan_router,
    )

    return application


app = create_app()