# syntax=docker/dockerfile:1

FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN groupadd \
        --system \
        --gid 10001 \
        appuser \
    && useradd \
        --system \
        --uid 10001 \
        --gid appuser \
        --create-home \
        --home-dir /home/appuser \
        appuser \
    && mkdir -p \
        /app/data \
        /app/artifacts/models \
        /home/appuser/.cache \
    && chown -R appuser:appuser \
        /app/data \
        /app/artifacts \
        /home/appuser

COPY pyproject.toml ./
COPY app ./app

RUN python -m pip install \
        --upgrade pip \
    && python -m pip install .

COPY contracts ./contracts
COPY configs ./configs
COPY validation ./validation

USER appuser

EXPOSE 8000

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=30s \
    --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health/ready', timeout=5)"]

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]