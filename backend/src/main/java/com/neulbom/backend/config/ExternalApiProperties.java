package com.neulbom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.external-api")
public record ExternalApiProperties(
        Duration connectTimeout,
        Duration readTimeout,
        int retryCount,
        boolean allowFallback,
        String whisperApiKey,
        String whisperBaseUrl,
        String whisperModel,
        String astApiUrl,
        String astApiKey,
        String astModel,
        String kcElectraApiUrl,
        String kcElectraApiKey,
        String kcElectraModel,
        String geminiApiKey,
        String geminiBaseUrl,
        String geminiModel
) {

    public boolean whisperConfigured() {
        return hasText(whisperApiKey);
    }

    public boolean astConfigured() {
        return hasText(astApiUrl);
    }

    public boolean kcElectraConfigured() {
        return hasText(kcElectraApiUrl);
    }

    public boolean geminiConfigured() {
        return hasText(geminiApiKey);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
