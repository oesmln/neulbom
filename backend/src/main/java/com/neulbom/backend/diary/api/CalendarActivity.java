package com.neulbom.backend.diary.api;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CalendarActivity(
        UUID activityId,
        String activityType,
        UUID referenceId,
        LocalDate activityDate,
        String title,
        String status,
        JsonNode metadata
) {
}
