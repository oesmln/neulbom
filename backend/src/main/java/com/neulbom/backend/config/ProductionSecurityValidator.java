package com.neulbom.backend.config;

import java.net.URI;
import java.util.Locale;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class ProductionSecurityValidator implements InitializingBean {

    static final String DEFAULT_JWT_SECRET = "local-only-change-this-jwt-secret-key-please";

    private final JwtProperties jwtProperties;
    private final AiServerProperties aiServerProperties;
    private final CorsProperties corsProperties;
    private final StorageProperties storageProperties;

    public ProductionSecurityValidator(
            JwtProperties jwtProperties,
            AiServerProperties aiServerProperties,
            CorsProperties corsProperties,
            StorageProperties storageProperties
    ) {
        this.jwtProperties = jwtProperties;
        this.aiServerProperties = aiServerProperties;
        this.corsProperties = corsProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void afterPropertiesSet() {
        requireSecret("JWT_SECRET", jwtProperties.secret());
        if (DEFAULT_JWT_SECRET.equals(jwtProperties.secret())) {
            throw unsafe("운영 환경에서 기본 JWT secret을 사용할 수 없습니다.");
        }

        if (!aiServerProperties.enabled()) {
            throw unsafe("운영 환경에서는 통합 AI 서버가 활성화되어야 합니다.");
        }
        if (aiServerProperties.allowInsecureLocalAudioUrl()) {
            throw unsafe("운영 환경에서는 로컬 HTTP 음성 URL을 허용할 수 없습니다.");
        }
        requireHttps("AI_SERVER_BASE_URL", aiServerProperties.baseUrl());
        requireSecret("AI_SERVER_SERVICE_TOKEN", aiServerProperties.serviceToken());
        requireHttps("AI_AUDIO_PUBLIC_BASE_URL", aiServerProperties.audioPublicBaseUrl());
        requireSecret("AI_AUDIO_SIGNING_SECRET", aiServerProperties.audioSigningSecret());

        if ("local".equalsIgnoreCase(storageProperties.type())) {
            throw unsafe("운영 환경에서는 로컬 파일 저장소를 사용할 수 없습니다.");
        }

        if (corsProperties.allowedOrigins() == null || corsProperties.allowedOrigins().isEmpty()) {
            throw unsafe("운영 CORS origin이 한 개 이상 필요합니다.");
        }
        for (String origin : corsProperties.allowedOrigins()) {
            URI uri = parseUri("CORS_ALLOWED_ORIGINS", origin);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || isLoopbackHost(host)
                    || "*".equals(origin.trim())) {
                throw unsafe("운영 CORS origin은 공개 HTTPS 주소만 허용합니다: " + origin);
            }
        }
    }

    private void requireSecret(String name, String value) {
        if (!StringUtils.hasText(value) || value.length() < 32) {
            throw unsafe(name + "은 32자 이상의 secret이어야 합니다.");
        }
    }

    private void requireHttps(String name, String value) {
        URI uri = parseUri(name, value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || isLoopbackHost(uri.getHost())) {
            throw unsafe(name + "은 유효한 HTTPS URL이어야 합니다.");
        }
    }

    private URI parseUri(String name, String value) {
        if (!StringUtils.hasText(value)) {
            throw unsafe(name + " 설정이 필요합니다.");
        }
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw unsafe(name + " 형식이 올바르지 않습니다.");
        }
    }

    private boolean isLoopbackHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized);
    }

    private IllegalStateException unsafe(String message) {
        return new IllegalStateException("안전하지 않은 운영 설정: " + message);
    }
}
