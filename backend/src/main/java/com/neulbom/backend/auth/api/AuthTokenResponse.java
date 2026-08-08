package com.neulbom.backend.auth.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("user_id") UUID userId,
        String role,
        @JsonProperty("profile_completed") boolean profileCompleted,
        @JsonProperty("is_new_user") boolean isNewUser
) {
}
