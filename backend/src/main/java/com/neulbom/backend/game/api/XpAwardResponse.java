package com.neulbom.backend.game.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record XpAwardResponse(int xpCurrent, int level, boolean levelUp, boolean deduplicated) {
}
