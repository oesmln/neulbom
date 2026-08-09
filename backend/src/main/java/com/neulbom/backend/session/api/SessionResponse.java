package com.neulbom.backend.session.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionResponse(
        UUID sessionId,
        UUID userId,
        String sessionType,
        String status,
        int currentQuestionOrder,
        int answeredCount,
        int totalQuestions,
        String recordingSyncStatus,
        SessionSettings settings,
        Instant startedAt,
        Instant endedAt
) {
}
