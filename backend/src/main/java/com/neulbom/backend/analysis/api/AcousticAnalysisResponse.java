package com.neulbom.backend.analysis.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcousticAnalysisResponse(
        UUID acousticAnalysisId,
        BigDecimal acousticReferenceScore,
        JsonNode acousticFlags,
        BigDecimal speechRate,
        BigDecimal pauseRatio,
        BigDecimal energyVariability,
        BigDecimal speechStability,
        Instant analyzedAt
) {
}
