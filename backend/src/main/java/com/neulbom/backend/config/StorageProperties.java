package com.neulbom.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String type,
        String localRoot,
        String bucket,
        DataSize maxFileSize,
        List<String> allowedMimeTypes,
        List<String> allowedExtensions
) {
}
