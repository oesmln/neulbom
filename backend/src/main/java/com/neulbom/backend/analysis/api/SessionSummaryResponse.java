package com.neulbom.backend.analysis.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionSummaryResponse(
        UUID summaryId,
        UUID sessionId,
        String summary,
        BigDecimal vocabularyScore,
        List<String> keywordFlags,
        int qaCount,
        String sourceStatus,
        Instant createdAt
) {
}
