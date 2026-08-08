package com.neulbom.backend.diary.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GenerationStatusResponse(
        UUID generationJobId,
        LocalDate targetDate,
        String status,
        Instant scheduledAt,
        Instant availableAt,
        UUID diaryId,
        String failureReason,
        boolean retryable,
        String displayLabel,
        String message
) {
}
