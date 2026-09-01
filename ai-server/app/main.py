from fastapi import FastAPI

from app.api.routes.health import router as health_router
from app.core.runtime import RuntimeState, lifespan


def create_app() -> FastAPI:
    """FastAPI 애플리케이션을 생성한다."""
    application = FastAPI(
        title="늘봄 AI 서버",
        version="0.1.0",
        description="음성·텍스트 기반 AI 분석 API",
        lifespan=lifespan,
    )

    # lifespan 실행 전에도 health 라우터가 안전하게 접근할 수 있게 한다.
    application.state.runtime_state = RuntimeState()
    application.include_router(health_router)

    return application


app = create_app()