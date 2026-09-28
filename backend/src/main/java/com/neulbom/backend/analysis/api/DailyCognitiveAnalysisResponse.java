package com.neulbom.backend.analysis.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Status-only response for the silent daily screening workflow; model scores stay in guardian reports. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DailyCognitiveAnalysisResponse(
        UUID analysisId,
        UUID sessionId,
        String status,
        int retryCount,
        boolean retryable,
        String reasonCode,
        Instant createdAt,
        Instant updatedAt
) {
}
