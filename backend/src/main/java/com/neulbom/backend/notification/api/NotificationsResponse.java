package com.neulbom.backend.notification.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NotificationsResponse(
        List<NotificationResponse> notifications,
        long unreadCount
) {
}
