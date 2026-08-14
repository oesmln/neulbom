package com.neulbom.backend.auth.service;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Replace with the production email adapter without changing auth rules. */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailVerificationNotifier implements EmailVerificationNotifier {

    @Override
    public void send(String email, String verificationToken, Instant expiresAt) {
        // Never log the raw token. A configured email provider delivers it.
    }
}
