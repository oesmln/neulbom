package com.neulbom.backend.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @JsonProperty("current_password") String currentPassword,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty("new_password") String newPassword,
        @JsonProperty("logout_other_sessions") Boolean logoutOtherSessions
) {

    public boolean shouldLogoutOtherSessions() {
        return logoutOtherSessions == null || logoutOtherSessions;
    }
}
