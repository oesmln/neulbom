package com.neulbom.backend.counseling.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CounselingAppointmentCreateRequest(
        @NotNull UUID elderId,
        @NotNull UUID centerId,
        @NotNull @Future Instant appointmentAt,
        @NotBlank String consultationType,
        @Size(max = 1000) String note,
        @NotNull Boolean privacyAgreed
) { }
