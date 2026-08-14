package com.neulbom.backend.speech.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SpeechSynthesizeRequest(
        @NotBlank @Size(max = 2000) String text,
        @Size(max = 64) String voiceProfileId,
        @DecimalMin("0.75") @DecimalMax("1.25") BigDecimal speechRate
) {
}
