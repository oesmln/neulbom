package com.neulbom.backend.analysis.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CognitiveAnalysisRequest(
        @NotNull UUID transcriptId,
        @NotBlank String transcript,
        @NotNull UUID userId,
        @NotNull UUID sessionId,
        UUID questionId,
        @NotBlank String questionType,
        UUID acousticAnalysisId,
        String fusionMode
) {
}
