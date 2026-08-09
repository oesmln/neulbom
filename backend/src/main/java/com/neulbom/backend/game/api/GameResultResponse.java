package com.neulbom.backend.game.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GameResultResponse(
        UUID gameResultId,
        BigDecimal cognitiveIndex,
        int xpEarned,
        int characterLevel,
        boolean levelUp,
        boolean deduplicated
) {
}
