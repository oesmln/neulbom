package com.neulbom.backend.diary.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReactionResponse(
        UUID reactionId,
        UUID diaryId,
        UUID reactorId,
        String reactorName,
        String reactionType,
        String message,
        Instant createdAt
) {
}
