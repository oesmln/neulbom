package com.neulbom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.password-reset-rate-limit")
public record PasswordResetRateLimitProperties(
        int emailMaxAttempts,
        Duration emailWindow,
        int ipMaxAttempts,
        Duration ipWindow
) {

    public PasswordResetRateLimitProperties {
        if (emailMaxAttempts < 1 || ipMaxAttempts < 1) {
            throw new IllegalArgumentException("password reset rate limit attempts must be positive");
        }
        if (invalid(emailWindow) || invalid(ipWindow)) {
            throw new IllegalArgumentException("password reset rate limit windows must be positive");
        }
    }

    private static boolean invalid(Duration duration) {
        return duration == null || duration.isZero() || duration.isNegative();
    }
}
