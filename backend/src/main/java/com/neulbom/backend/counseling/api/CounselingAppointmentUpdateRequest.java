package com.neulbom.backend.counseling.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CounselingAppointmentUpdateRequest(
        @Future Instant appointmentAt,
        String consultationType,
        @Size(max = 1000) String note
) { }
