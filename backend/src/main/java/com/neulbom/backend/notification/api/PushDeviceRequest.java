package com.neulbom.backend.notification.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PushDeviceRequest(
        @NotBlank @Pattern(regexp = "(?:Expo|Exponent)PushToken\\[[A-Za-z0-9_-]+\\]") String expoPushToken,
        @NotBlank @Pattern(regexp = "ios|android") String platform
) {
}
