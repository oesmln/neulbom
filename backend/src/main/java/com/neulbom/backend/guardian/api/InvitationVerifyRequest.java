package com.neulbom.backend.guardian.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InvitationVerifyRequest(
        @NotBlank @Pattern(regexp = "\\d{6}") String inviteCode
) {
}
