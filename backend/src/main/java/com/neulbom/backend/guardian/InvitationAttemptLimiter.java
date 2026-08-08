package com.neulbom.backend.guardian;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.neulbom.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InvitationAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InvitationAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String clientIp) {
        Window window = currentWindow(clientIp);
        if (window.failures >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "초대 코드 검증 시도가 너무 많습니다.", "잠시 후 다시 시도하세요.");
        }
    }

    public void recordFailure(String clientIp) {
        currentWindow(clientIp).failures++;
    }

    public void recordSuccess(String clientIp) {
        windows.remove(normalize(clientIp));
    }

    private Window currentWindow(String clientIp) {
        String key = normalize(clientIp);
        Instant now = clock.instant();
        return windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.startedAt.plus(WINDOW).isBefore(now)) {
                return new Window(now);
            }
            return existing;
        });
    }

    private String normalize(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private static final class Window {
        private final Instant startedAt;
        private int failures;

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
