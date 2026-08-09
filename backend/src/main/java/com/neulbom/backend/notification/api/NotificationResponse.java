package com.neulbom.backend.notification.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationResponse(
        UUID notificationId,
        String title,
        String body,
        String type,
        String severity,
        String statusLabel,
        JsonNode data,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {
}
