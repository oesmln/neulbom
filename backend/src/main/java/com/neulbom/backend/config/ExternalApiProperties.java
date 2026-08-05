package com.neulbom.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.external-api")
public record ExternalApiProperties(
        Duration connectTimeout,
        Duration readTimeout,
        int retryCount
) {
}
