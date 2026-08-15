package com.neulbom.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level mail delivery settings. SMTP credentials belong to
 * spring.mail and are injected from environment variables; these properties
 * only describe whether delivery is enabled and where links should point.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        boolean enabled,
        String from,
        String passwordResetUrl,
        String emailVerificationUrl
) {
}
