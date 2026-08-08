package com.neulbom.backend.game.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record XpAwardRequest(int amount, @NotBlank String reason, @NotBlank String eventId) {
}
