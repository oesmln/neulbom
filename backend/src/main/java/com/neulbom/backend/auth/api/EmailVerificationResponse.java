package com.neulbom.backend.auth.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmailVerificationResponse(
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("expires_at") Instant expiresAt
) { }
