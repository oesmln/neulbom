package com.neulbom.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.request-id")
public record RequestIdProperties(String headerName) {
}
