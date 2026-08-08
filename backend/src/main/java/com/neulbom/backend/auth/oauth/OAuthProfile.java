package com.neulbom.backend.auth.oauth;

public record OAuthProfile(
        String provider,
        String providerUserId,
        String email,
        String displayName,
        boolean emailVerified
) {
}
