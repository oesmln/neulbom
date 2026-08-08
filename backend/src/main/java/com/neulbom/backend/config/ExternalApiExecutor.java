package com.neulbom.backend.config;

import java.time.Duration;
import java.util.function.Supplier;

import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ExternalApiExecutor {

    private final ExternalApiProperties properties;

    public ExternalApiExecutor(ExternalApiProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(String provider, Supplier<T> request) {
        int maxAttempts = Math.max(1, properties.retryCount() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return request.get();
            } catch (RestClientResponseException exception) {
                if (!retryable(exception.getStatusCode()) || attempt == maxAttempts) {
                    throw unavailable(provider, exception);
                }
                pause(attempt);
            } catch (RestClientException exception) {
                if (attempt == maxAttempts) {
                    throw unavailable(provider, exception);
                }
                pause(attempt);
            }
        }
        throw new ExternalServiceUnavailableException(provider + " provider를 사용할 수 없습니다.");
    }

    private boolean retryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(Duration.ofMillis(Math.min(500L, 100L * attempt)).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceUnavailableException("외부 provider 재시도가 중단되었습니다.");
        }
    }

    private ExternalServiceUnavailableException unavailable(String provider, Exception exception) {
        String status = exception instanceof RestClientResponseException response
                ? " (status=" + response.getStatusCode().value() + ")" : "";
        return new ExternalServiceUnavailableException(provider + " provider 요청에 실패했습니다." + status);
    }
}
