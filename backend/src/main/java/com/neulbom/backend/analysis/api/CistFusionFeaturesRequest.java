package com.neulbom.backend.analysis.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CistFusionFeaturesRequest(
        @NotNull UUID userId,
        @NotNull UUID sessionId,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal astScore,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal kcElectraScore,
        @NotBlank String featureVersion,
        String scalerVersion
) {
}
