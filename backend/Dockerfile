# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY backend/gradlew backend/settings.gradle backend/build.gradle ./backend/
COPY backend/gradle ./backend/gradle
COPY ai-server/contracts ./ai-server/contracts

WORKDIR /workspace/backend
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY backend/src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid appuser --home-dir /app appuser \
    && mkdir -p /app /data/uploads \
    && chown -R appuser:appuser /app /data/uploads

WORKDIR /app

COPY --from=build --chown=appuser:appuser /workspace/backend/build/libs/*.jar app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
