package com.neulbom.backend.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of the provider authentication step.
 *
 * Existing social accounts are fully authenticated here.  A first-time
 * social account receives a short-lived, one-time pending token so the app
 * can ask for the role after the provider login has already completed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthPrepareResponse(
        String status,
        AuthTokenResponse tokens,
        @JsonProperty("pending_token") String pendingToken,
        String email,
        @JsonProperty("display_name") String displayName
) {

    public static OAuthPrepareResponse authenticated(AuthTokenResponse tokens) {
        return new OAuthPrepareResponse("authenticated", tokens, null, null, null);
    }

    public static OAuthPrepareResponse roleRequired(String pendingToken, String email, String displayName) {
        return new OAuthPrepareResponse("role_required", null, pendingToken, email, displayName);
    }
}
