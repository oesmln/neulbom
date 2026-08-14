package com.neulbom.backend.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OAuthCompleteRequest(
        @NotBlank @JsonProperty("pending_token") String pendingToken,
        @NotBlank @Pattern(regexp = "elder|guardian") @JsonProperty("role") String role
) {
}
