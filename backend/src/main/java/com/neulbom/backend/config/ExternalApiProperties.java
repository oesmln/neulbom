package com.neulbom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.external-api")
public record ExternalApiProperties(
        Duration connectTimeout,
        Duration readTimeout,
        int retryCount,
        boolean allowFallback,
        String sttProvider,
        String whisperApiKey,
        String whisperBaseUrl,
        String whisperModel,
        String localWhisperBaseUrl,
        String localWhisperApiKey,
        String localWhisperModel,
        String googleSttBaseUrl,
        String googleSttProjectId,
        String googleSttLocation,
        String googleSttModel,
        String googleSttLanguageCode,
        String googleTtsBaseUrl,
        String googleTtsProjectId,
        String googleTtsLanguageCode,
        String googleTtsDefaultVoice,
        String googleTtsClearVoice,
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

    public boolean localWhisperConfigured() {
        return hasText(localWhisperBaseUrl);
    }

    public boolean googleSttConfigured() {
        return hasText(googleSttProjectId);
    }

    public String normalizedSttProvider() {
        return hasText(sttProvider) ? sttProvider.trim().toLowerCase(java.util.Locale.ROOT) : "google";
    }

    public boolean googleTtsConfigured() {
        return hasText(resolvedGoogleTtsProjectId());
    }

    public String resolvedGoogleTtsProjectId() {
        return hasText(googleTtsProjectId) ? googleTtsProjectId.trim() : googleSttProjectId;
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
