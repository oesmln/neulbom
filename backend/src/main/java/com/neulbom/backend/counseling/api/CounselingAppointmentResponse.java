package com.neulbom.backend.counseling.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CounselingAppointmentResponse(
        UUID appointmentId,
        UUID guardianId,
        UUID elderId,
        UUID centerId,
        String centerName,
        String centerAddress,
        Instant appointmentAt,
        String consultationType,
        String status,
        String note,
        Instant createdAt,
        Instant updatedAt
) { }
