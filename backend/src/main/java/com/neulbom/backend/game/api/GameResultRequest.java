package com.neulbom.backend.game.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GameResultRequest(
        @NotNull UUID userId,
        @NotNull UUID sessionId,
        @NotNull UUID clientGameResultId,
        @NotNull String gameType,
        int score,
        @NotNull List<BigDecimal> responseTimes,
        int errorCount,
        int totalQuestions,
        Integer matchedPairs,
        Integer attemptCount,
        int durationSec,
        Integer restartedCount,
        boolean completed
) {
}
