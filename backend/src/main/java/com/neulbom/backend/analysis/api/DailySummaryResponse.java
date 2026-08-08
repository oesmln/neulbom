package com.neulbom.backend.analysis.api;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DailySummaryResponse(
        UUID dailySummaryId,
        UUID userId,
        LocalDate localDate,
        String timezone,
        int sessionCount,
        int analyzedSessionCount,
        String status,
        String displayLabel,
        String message,
        String recommendation
) {
}
