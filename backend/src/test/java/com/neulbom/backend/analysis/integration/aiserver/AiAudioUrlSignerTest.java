package com.neulbom.backend.analysis.integration.aiserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.config.AiServerProperties;
import com.neulbom.backend.recording.RecordingEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiAudioUrlSignerTest {

    private static final Instant NOW = Instant.parse("2026-09-05T09:00:00Z");

    @Test
    void issuesAnHttpsUrlAndAcceptsOnlyItsUnexpiredSignature() {
        UUID recordingId = UUID.randomUUID();
        AiAudioUrlSigner signer = signer("https://backend.test", "test-signing-secret-at-least-32-characters");
        RecordingEntity recording = recording(recordingId, "audio/x-wav");

        var audio = signer.issue(recording);

        assertThat(audio.signedUrl().getScheme()).isEqualTo("https");
        assertThat(audio.contentType()).isEqualTo("audio/wav");
        assertThat(audio.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        String query = audio.signedUrl().getQuery();
        long expiresAt = Long.parseLong(query.replaceAll(".*expires_at=([0-9]+).*", "$1"));
        String signature = query.replaceAll(".*signature=([a-f0-9]+).*", "$1");
        signer.verify(recordingId, expiresAt, signature);

        assertThatThrownBy(() -> signer.verify(recordingId, expiresAt, "invalid"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void rejectsNonHttpsPublicOrigins() {
        AiAudioUrlSigner signer = signer("http://host.docker.internal:8080", "test-signing-secret-at-least-32-characters");

        assertThatThrownBy(() -> signer.issue(recording(UUID.randomUUID(), "audio/mp4")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void acceptsLocalHttpOriginWhenLocalModeIsEnabled() {
        AiAudioUrlSigner signer = signer(
                "http://host.docker.internal:8080",
                "test-signing-secret-at-least-32-characters",
                true);

        var audio = signer.issue(recording(UUID.randomUUID(), "audio/mp4"));

        assertThat(audio.signedUrl().getScheme()).isEqualTo("http");
        assertThat(audio.signedUrl().getHost()).isEqualTo("host.docker.internal");
    }

    @Test
    void rejectsPublicHttpOriginEvenWhenLocalModeIsEnabled() {
        AiAudioUrlSigner signer = signer(
                "http://backend.example.com:8080",
                "test-signing-secret-at-least-32-characters",
                true);

        assertThatThrownBy(() -> signer.issue(recording(UUID.randomUUID(), "audio/mp4")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void acceptsWebmAudioFormatInTheAiContract() {
        AiAudioUrlSigner signer = signer("https://backend.test", "test-signing-secret-at-least-32-characters");

        var audio = signer.issue(recording(UUID.randomUUID(), "audio/webm"));

        assertThat(audio.contentType()).isEqualTo("audio/webm");
    }

    private AiAudioUrlSigner signer(String publicBaseUrl, String secret) {
        return signer(publicBaseUrl, secret, false);
    }

    private AiAudioUrlSigner signer(String publicBaseUrl, String secret, boolean allowLocalHttp) {
        return new AiAudioUrlSigner(
                new AiServerProperties(
                        true,
                        "http://localhost:8000",
                        "service-token",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30),
                        2,
                        Duration.ofMinutes(30),
                        publicBaseUrl,
                        secret,
                        allowLocalHttp),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RecordingEntity recording(UUID recordingId, String mimeType) {
        return new RecordingEntity(
                recordingId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                RecordingEntity.ANSWER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "recordings/test.wav",
                "test.wav",
                "{}",
                mimeType,
                1234,
                4000,
                NOW,
                NOW);
    }
}
