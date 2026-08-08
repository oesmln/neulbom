package com.neulbom.backend.analysis.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QaPair(
        @NotNull UUID questionId,
        @NotBlank String question,
        @NotBlank String answer,
        String questionType
) {
}
