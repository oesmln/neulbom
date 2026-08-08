package com.neulbom.backend.user.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserProfileUpdateResponse(
        UUID userId,
        boolean profileCompleted,
        Instant updatedAt
) {
}
