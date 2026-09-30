package com.neulbom.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String type,
        String localRoot,
        String bucket,
        DataSize maxFileSize,
        List<String> allowedMimeTypes,
        List<String> allowedExtensions,
        String encryptionKey
) {

    @ConstructorBinding
    public StorageProperties {
    }

    /** Keeps focused validator tests source-compatible; production binds its key from configuration. */
    public StorageProperties(
            String type,
            String localRoot,
            String bucket,
            DataSize maxFileSize,
            List<String> allowedMimeTypes,
            List<String> allowedExtensions
    ) {
        this(type, localRoot, bucket, maxFileSize, allowedMimeTypes, allowedExtensions,
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
    }
}
