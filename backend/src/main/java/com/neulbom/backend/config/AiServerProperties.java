package com.neulbom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.ai-server")
public record AiServerProperties(
        boolean enabled,
        String baseUrl,
        String serviceToken,
        Duration connectTimeout,
        Duration readTimeout,
        int retryCount,
        Duration signedUrlTtl,
        String audioPublicBaseUrl,
        String audioSigningSecret
) {

    public boolean configured() {
        return enabled && StringUtils.hasText(baseUrl) && StringUtils.hasText(serviceToken);
    }

    public boolean audioSigningConfigured() {
        return StringUtils.hasText(audioPublicBaseUrl) && StringUtils.hasText(audioSigningSecret);
    }
}
