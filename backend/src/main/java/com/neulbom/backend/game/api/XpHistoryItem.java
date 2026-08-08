package com.neulbom.backend.game.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record XpHistoryItem(UUID xpLedgerId, String reason, String displayTitle, int amount, String eventId, Instant earnedAt) {
}
