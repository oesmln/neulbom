package com.neulbom.backend.analysis.api;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CistRetestScheduleResponse(
        UUID lastCompletedSessionId,
        LocalDate lastCompletedDate,
        LocalDate nextDueDate,
        boolean retestDue,
        String timezone
) { }
