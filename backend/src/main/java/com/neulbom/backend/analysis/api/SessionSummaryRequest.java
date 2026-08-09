package com.neulbom.backend.analysis.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionSummaryRequest(
        @NotNull UUID sessionId,
        @NotNull UUID userId,
        @NotEmpty List<@Valid QaPair> qaPairs
) {
}
