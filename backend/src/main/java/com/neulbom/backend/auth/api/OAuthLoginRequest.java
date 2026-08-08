package com.neulbom.backend.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OAuthLoginRequest(
        @NotBlank @JsonProperty("authorization_code") String authorizationCode,
        @NotBlank @JsonProperty("redirect_uri") String redirectUri,
        @Pattern(regexp = "elder|guardian") @JsonProperty("role") String role,
        @JsonProperty("state") String state
) {
}
