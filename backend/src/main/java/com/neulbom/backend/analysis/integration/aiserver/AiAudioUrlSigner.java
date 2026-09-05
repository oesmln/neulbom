package com.neulbom.backend.analysis.integration.aiserver;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AudioResource;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.AiServerProperties;
import com.neulbom.backend.recording.RecordingEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiAudioUrlSigner {

    private final AiServerProperties properties;
    private final Clock clock;

    public AiAudioUrlSigner(AiServerProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public AudioResource issue(RecordingEntity recording) {
        requireConfigured();
        Instant expiresAt = clock.instant().plus(properties.signedUrlTtl());
        long expiresEpoch = expiresAt.getEpochSecond();
        String signature = signature(recording.getId(), expiresEpoch);
        String baseUrl = properties.audioPublicBaseUrl().trim().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl + "/api/v1/internal/ai-audio/" + recording.getId()
                + "?expires_at=" + expiresEpoch + "&signature=" + signature);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ExternalServiceUnavailableException("AI 음성 공개 주소는 HTTPS여야 합니다.");
        }
        return new AudioResource(
                uri,
                expiresAt,
                standardMimeType(recording.getMimeType()),
                recording.getFileSizeBytes(),
                null);
    }

    public void verify(UUID recordingId, long expiresEpoch, String suppliedSignature) {
        requireConfigured();
        if (expiresEpoch <= clock.instant().getEpochSecond()) {
            throw new ApiException(HttpStatus.GONE, "음성 URL이 만료되었습니다.", "새 signed URL을 발급받으세요.");
        }
        byte[] expected = signature(recordingId, expiresEpoch).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = StringUtils.hasText(suppliedSignature)
                ? suppliedSignature.getBytes(StandardCharsets.US_ASCII) : new byte[0];
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "음성 URL 서명이 올바르지 않습니다.", "signed URL을 확인하세요.");
        }
    }

    private String signature(UUID recordingId, long expiresEpoch) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.audioSigningSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (recordingId + ":" + expiresEpoch).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ExternalServiceUnavailableException("AI 음성 URL 서명을 생성할 수 없습니다.");
        }
    }

    private void requireConfigured() {
        if (!properties.audioSigningConfigured() || properties.audioSigningSecret().length() < 32) {
            throw new ExternalServiceUnavailableException("AI 음성 signed URL 설정이 완료되지 않았습니다.");
        }
    }

    private String standardMimeType(String mimeType) {
        return switch (mimeType == null ? "" : mimeType.toLowerCase(java.util.Locale.ROOT)) {
            case "audio/x-wav" -> "audio/wav";
            case "audio/x-m4a" -> "audio/mp4";
            case "audio/wav", "audio/mp4", "audio/mpeg" -> mimeType.toLowerCase(java.util.Locale.ROOT);
            default -> throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "AI 분석에서 지원하지 않는 음성 형식입니다.",
                    "WAV, M4A 또는 MP3 녹음을 사용하세요.");
        };
    }
}
