package com.neulbom.backend.guardian.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GuardianLinkRequest(
        @NotNull UUID elderId,
        UUID guardianId,
        @Size(max = 100) String relation,
        List<String> accessScope
) {
}
