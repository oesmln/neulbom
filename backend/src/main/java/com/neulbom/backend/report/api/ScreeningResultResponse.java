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
public record ScreeningResultResponse(
        String audience,
        UUID sessionId,
        UUID userId,
        String sessionType,
        String resultStatus,
        String resultType,
        String displayLabel,
        String message,
        String recommendation,
        BigDecimal screeningReferenceScore,
        BigDecimal displayScore,
        BigDecimal scoreMax,
        BigDecimal scoreRate,
        String riskLevel,
        String screeningLabel,
        JsonNode domainScores,
        Instant completedAt,
        UUID summaryId
) {
}
