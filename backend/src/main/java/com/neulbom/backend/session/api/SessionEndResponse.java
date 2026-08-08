package com.neulbom.backend.session.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionEndResponse(
        UUID sessionId,
        String status,
        Instant endedAt,
        int answeredCount,
        String analysisStatus,
        String resultStatus,
        String resultType,
        String displayLabel,
        String message,
        String recommendation,
        int xpEarned,
        Integer characterLevel,
        boolean levelUp
) {
}
