package com.neulbom.backend.session.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionSettings(
        String voiceProfileId,
        String preferredHearingSide,
        BigDecimal speechRate,
        boolean subtitleEnabled,
        boolean soundEffectEnabled
) {
}
