package com.neulbom.backend.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryRecordResponse(
        UUID analysisId,
        UUID sessionId,
        BigDecimal screeningReferenceScore,
        BigDecimal displayScore,
        BigDecimal scoreMax,
        BigDecimal scoreRate,
        String label,
        String riskLevel,
        JsonNode domainScores,
        String trend,
        BigDecimal averageScore30d,
        BigDecimal scoreDelta,
        Instant analyzedAt
) {
}
