package com.neulbom.backend.analysis.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CistAiAnalysisResponse(
        UUID analysisId,
        UUID sessionId,
        String status,
        int retryCount,
        boolean retryable,
        String reasonCode,
        JsonNode retryItems,
        JsonNode result,
        BigDecimal modelScore,
        String modelVersion,
        BigDecimal decisionThreshold,
        BigDecimal reviewThreshold,
        String thresholdVersion,
        Boolean riskFlag,
        String riskLevel,
        Instant createdAt,
        Instant updatedAt
) {
}
