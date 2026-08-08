package com.neulbom.backend.session.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionStartRequest(
        @NotNull UUID userId,
        String sessionType,
        String voiceProfileId,
        String preferredHearingSide,
        Boolean subtitleEnabled,
        Boolean offlineMode
) {
}
