package com.neulbom.backend.auth.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterResponse(
        @JsonProperty("user_id") UUID userId,
        String role,
        @JsonProperty("profile_completed") boolean profileCompleted,
        @JsonProperty("created_at") Instant createdAt
) {
}
