package com.neulbom.backend.guardian.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InvitationVerifyResponse(
        UUID invitationId,
        String status,
        String relation,
        List<String> accessScope,
        Instant expiresAt,
        boolean requiresConsent
) {
}
