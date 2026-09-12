package com.neulbom.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class ProductionSecurityValidatorTest {

    @Test
    void acceptsSecureProductionConfiguration() {
        assertThatCode(() -> validator(
                secureJwt(),
                secureAi(),
                secureCors(),
                objectStorage()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDefaultJwtSecret() {
        JwtProperties jwt = new JwtProperties(
                ProductionSecurityValidator.DEFAULT_JWT_SECRET,
                "neulbom",
                Duration.ofMinutes(15),
                Duration.ofDays(30));

        assertRejected(jwt, secureAi(), secureCors(), objectStorage(), "기본 JWT");
    }

    @Test
    void rejectsHttpAiServerUrl() {
        AiServerProperties ai = new AiServerProperties(
                true,
                "http://ai-server:8000",
                "s".repeat(32),
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                2,
                Duration.ofMinutes(5),
                "https://api.example.com",
                "a".repeat(32),
                false);

        assertRejected(secureJwt(), ai, secureCors(), objectStorage(), "HTTPS");
    }

    @Test
    void rejectsLocalAudioModeInProduction() {
        AiServerProperties ai = new AiServerProperties(
                true,
                "https://ai.example.com",
                "s".repeat(32),
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                2,
                Duration.ofMinutes(5),
                "https://api.example.com",
                "a".repeat(32),
                true);

        assertRejected(secureJwt(), ai, secureCors(), objectStorage(), "로컬 HTTP");
    }

    @Test
    void rejectsBlankOrShortServiceSecrets() {
        AiServerProperties ai = new AiServerProperties(
                true,
                "https://ai.example.com",
                "short",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                2,
                Duration.ofMinutes(5),
                "https://api.example.com",
                "short",
                false);

        assertRejected(secureJwt(), ai, secureCors(), objectStorage(), "32자");
    }

    @Test
    void rejectsLocalhostCorsOrigin() {
        CorsProperties cors = new CorsProperties(
                List.of("http://localhost:8081"),
                List.of("GET"),
                List.of("Authorization"));

        assertRejected(secureJwt(), secureAi(), cors, objectStorage(), "CORS");
    }

    @Test
    void rejectsLocalStorage() {
        StorageProperties storage = new StorageProperties(
                "local",
                "./uploads",
                "neulbom",
                DataSize.ofMegabytes(25),
                List.of("audio/wav"),
                List.of("wav"));

        assertRejected(secureJwt(), secureAi(), secureCors(), storage, "로컬 파일 저장소");
    }

    private void assertRejected(
            JwtProperties jwt,
            AiServerProperties ai,
            CorsProperties cors,
            StorageProperties storage,
            String expectedMessage
    ) {
        assertThatThrownBy(() -> validator(jwt, ai, cors, storage).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage);
    }

    private ProductionSecurityValidator validator(
            JwtProperties jwt,
            AiServerProperties ai,
            CorsProperties cors,
            StorageProperties storage
    ) {
        return new ProductionSecurityValidator(jwt, ai, cors, storage);
    }

    private JwtProperties secureJwt() {
        return new JwtProperties(
                "j".repeat(32),
                "neulbom",
                Duration.ofMinutes(15),
                Duration.ofDays(30));
    }

    private AiServerProperties secureAi() {
        return new AiServerProperties(
                true,
                "https://ai.example.com",
                "s".repeat(32),
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                2,
                Duration.ofMinutes(5),
                "https://api.example.com",
                "a".repeat(32),
                false);
    }

    private CorsProperties secureCors() {
        return new CorsProperties(
                List.of("https://app.example.com"),
                List.of("GET", "POST"),
                List.of("Authorization", "Content-Type"));
    }

    private StorageProperties objectStorage() {
        return new StorageProperties(
                "gcs",
                "./uploads",
                "neulbom-production",
                DataSize.ofMegabytes(25),
                List.of("audio/wav"),
                List.of("wav"));
    }
}
