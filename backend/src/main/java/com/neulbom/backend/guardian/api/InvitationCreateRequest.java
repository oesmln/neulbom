package com.neulbom.backend.guardian.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InvitationCreateRequest(
        @Size(max = 100) String relation,
        List<String> accessScope,
        @Min(60) @Max(3600) Integer expiresIn
) {
}
