package com.neulbom.backend.session.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnswerRequest(
        @NotNull UUID clientAnswerId,
        @NotNull UUID questionId,
        String answerText,
        UUID recordingId,
        UUID transcriptId,
        @Min(0) Integer responseTimeMs,
        @NotNull Instant answeredAt
) {
}
