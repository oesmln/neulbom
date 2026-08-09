package com.neulbom.backend.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import com.neulbom.backend.common.exception.RateLimitExceededException;
import com.neulbom.backend.config.PasswordResetRateLimitProperties;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimiter {

    private final PasswordResetRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public PasswordResetRateLimiter(PasswordResetRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void check(String email, String clientIp) {
        Instant now = Instant.now(clock);
        increment(
                "email:" + email.trim().toLowerCase(Locale.ROOT),
                properties.emailMaxAttempts(),
                properties.emailWindow(),
                now);
        increment("ip:" + clientIp, properties.ipMaxAttempts(), properties.ipWindow(), now);
    }

    private void increment(String key, int maxAttempts, java.time.Duration window, Instant now) {
        Window result = windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.expiresAt())) {
                return new Window(1, now.plus(window));
            }
            return new Window(current.attempts() + 1, current.expiresAt());
        });
        if (result.attempts() > maxAttempts) {
            throw new RateLimitExceededException(result.expiresAt().getEpochSecond() - now.getEpochSecond());
        }
    }

    private record Window(int attempts, Instant expiresAt) {
    }
}
