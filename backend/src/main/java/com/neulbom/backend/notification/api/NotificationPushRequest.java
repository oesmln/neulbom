package com.neulbom.backend.notification.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationPushRequest(
        @NotNull UUID targetUserId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String body,
        @NotBlank String type,
        String severity,
        @Size(max = 100) String statusLabel,
        JsonNode data
) {
}
