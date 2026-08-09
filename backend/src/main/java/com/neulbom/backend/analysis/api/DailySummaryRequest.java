package com.neulbom.backend.analysis.api;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DailySummaryRequest(
        @NotNull UUID userId,
        @NotNull LocalDate localDate,
        String timezone
) {
}
