package com.neulbom.backend.guardian.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GuardianLinkResponse(
        UUID invitationId,
        UUID linkId,
        UUID elderId,
        UUID guardianId,
        String status,
        List<String> accessScope,
        boolean consentRequired,
        Instant createdAt,
        Instant updatedAt
) {
}
