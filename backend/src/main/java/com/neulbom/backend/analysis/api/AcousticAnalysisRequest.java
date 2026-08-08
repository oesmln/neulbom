package com.neulbom.backend.analysis.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcousticAnalysisRequest(
        @NotNull UUID recordingId,
        @NotNull UUID userId,
        @NotNull UUID sessionId,
        @Min(1) @Max(60) Integer segmentLengthSec,
        String modelVersion
) {
}
