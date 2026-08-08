package com.neulbom.backend.guardian.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ElderSummaryResponse(
        UUID elderId,
        String elderName,
        UUID linkId,
        String status,
        List<String> accessScope,
        String consentStatus,
        Double latestDisplayScore,
        Double latestScoreMax,
        Double latestScoreRate,
        String latestRiskLevel,
        Instant lastSessionAt
) {
}
