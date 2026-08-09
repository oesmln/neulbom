package com.neulbom.backend.game.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CharacterResponse(
        UUID userId,
        String displayName,
        int level,
        String stage,
        int stageIndex,
        int stageCount,
        int xpCurrent,
        int xpGoal,
        int xpRemaining,
        String skinId,
        List<String> unlocked
) {
}
