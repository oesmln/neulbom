package com.neulbom.backend.user.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConsentRequest(
        @NotBlank @Size(max = 30) String consentType,
        @NotNull Boolean agreed,
        @NotNull Instant agreedAt,
        @NotBlank @Size(max = 50) String version
) {
}
