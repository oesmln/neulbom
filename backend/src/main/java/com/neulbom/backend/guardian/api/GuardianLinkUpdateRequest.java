package com.neulbom.backend.guardian.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GuardianLinkUpdateRequest(
        String status,
        List<String> accessScope,
        @Size(max = 100) String relation
) {
}
