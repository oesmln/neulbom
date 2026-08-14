package com.neulbom.backend.auth;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import com.neulbom.backend.auth.service.SmtpEmailVerificationNotifier;
import com.neulbom.backend.auth.service.SmtpMailSender;
import com.neulbom.backend.auth.service.SmtpPasswordResetNotifier;
import com.neulbom.backend.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SmtpEmailNotifierTest {

    private final MailProperties properties = new MailProperties(
            true,
            "no-reply@example.com",
            "https://app.example.com/reset-password",
            "https://app.example.com/email-verification");

    @Test
    void passwordResetNotifierSendsOneTimeLinkAndExpiry() {
        SmtpMailSender sender = Mockito.mock(SmtpMailSender.class);
        SmtpPasswordResetNotifier notifier = new SmtpPasswordResetNotifier(sender, properties);
        Instant expiresAt = Instant.parse("2026-08-15T03:00:00Z");

        notifier.send("user@example.com", "reset-token-123", expiresAt);

        verify(sender).send(
                eq("user@example.com"),
                eq("늘봄 비밀번호 재설정 안내"),
                contains("https://app.example.com/reset-password?token=reset-token-123"));
    }

    @Test
    void emailVerificationNotifierSendsVerificationLink() {
        SmtpMailSender sender = Mockito.mock(SmtpMailSender.class);
        SmtpEmailVerificationNotifier notifier = new SmtpEmailVerificationNotifier(sender, properties);

        notifier.send("user@example.com", "verification-token", Instant.parse("2026-08-15T03:00:00Z"));

        verify(sender).send(
                eq("user@example.com"),
                eq("늘봄 이메일 인증 안내"),
                contains("https://app.example.com/email-verification?token=verification-token"));
    }
}
