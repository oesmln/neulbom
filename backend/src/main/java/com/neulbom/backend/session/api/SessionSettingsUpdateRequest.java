package com.neulbom.backend.session.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionSettingsUpdateRequest(
        String preferredHearingSide,
        @Size(max = 64) String voiceProfileId,
        @DecimalMin("0.75") @DecimalMax("1.25") BigDecimal speechRate,
        Boolean subtitleEnabled,
        Boolean soundEffectEnabled
) {
}
