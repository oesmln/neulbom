from fastapi import FastAPI

from app.api.routes.health import router as health_router


def create_app() -> FastAPI:
    """FastAPI 애플리케이션 생성"""
    application = FastAPI(
        title="늘봄 AI 서버",
        version="0.1.0",
        description="음성·텍스트 기반 위험도 분석 API",
    )
    application.include_router(health_router)
    return application


app = create_app()
