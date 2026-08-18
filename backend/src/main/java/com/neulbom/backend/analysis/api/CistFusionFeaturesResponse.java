package com.neulbom.backend.analysis.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CistFusionFeaturesResponse(
        UUID featureId,
        UUID sessionId,
        UUID userId,
        BigDecimal astScore,
        BigDecimal kcElectraScore,
        BigDecimal categoryBalancedWrongEventScore,
        BigDecimal categoryBalancedMedianDelay,
        String featureVersion,
        String scalerVersion,
        Instant updatedAt
) {
}
