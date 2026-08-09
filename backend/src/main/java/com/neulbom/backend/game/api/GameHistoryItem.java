package com.neulbom.backend.game.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GameHistoryItem(
        UUID gameResultId,
        String gameType,
        int score,
        Integer matchedPairs,
        Integer attemptCount,
        int durationSec,
        int restartedCount,
        boolean completed,
        BigDecimal cognitiveIndex,
        int xpEarned,
        Instant playedAt
) {
}
