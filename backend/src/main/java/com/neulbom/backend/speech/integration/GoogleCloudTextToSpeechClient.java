package com.neulbom.backend.speech.integration;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** Google Cloud Text-to-Speech v1 adapter. Provider credentials never leave the backend. */
@Component
public class GoogleCloudTextToSpeechClient implements TextToSpeechClient {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final RestClient restClient;
    private final ExternalApiProperties properties;
    private final ExternalApiExecutor executor;
    private final ObjectMapper objectMapper;
    private final Supplier<String> accessTokenSupplier;

    @Autowired
    public GoogleCloudTextToSpeechClient(
            @Qualifier("externalRestClient") RestClient restClient,
            ExternalApiProperties properties,
            ExternalApiExecutor executor,
            ObjectMapper objectMapper
    ) {
        this(restClient, properties, executor, objectMapper,
                GoogleCloudTextToSpeechClient::applicationDefaultAccessToken);
    }

    GoogleCloudTextToSpeechClient(
            RestClient restClient,
            ExternalApiProperties properties,
            ExternalApiExecutor executor,
            ObjectMapper objectMapper,
            Supplier<String> accessTokenSupplier
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.accessTokenSupplier = accessTokenSupplier;
    }

    @Override
    public boolean isConfigured() {
        return properties.googleTtsConfigured();
    }

    @Override
    public SynthesisResult synthesize(
            String text,
            String languageCode,
            String voiceName,
            BigDecimal speechRate
    ) {
        if (!isConfigured()) {
            throw new ExternalServiceUnavailableException("Google TTS 프로젝트 ID가 설정되지 않았습니다.");
        }

        JsonNode response = executor.execute("Google TTS", () -> restClient.post()
                .uri(endpoint())
                .header("Authorization", "Bearer " + accessTokenSupplier.get())
                .header("x-goog-user-project", projectId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request(text, languageCode, voiceName, speechRate))
                .retrieve()
                .body(JsonNode.class));

        String audioContent = response == null ? "" : response.path("audioContent").asText("");
        if (!StringUtils.hasText(audioContent)) {
            throw new ExternalServiceUnavailableException("Google TTS 응답에 audioContent가 없습니다.");
        }
        try {
            byte[] audio = Base64.getDecoder().decode(audioContent);
            if (audio.length == 0) {
                throw new ExternalServiceUnavailableException("Google TTS가 빈 음성을 반환했습니다.");
            }
            return new SynthesisResult(audio, "audio/mpeg", voiceName);
        } catch (IllegalArgumentException exception) {
            throw new ExternalServiceUnavailableException("Google TTS 음성 데이터 형식이 올바르지 않습니다.");
        }
    }

    private ObjectNode request(String text, String languageCode, String voiceName, BigDecimal speechRate) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("input").put("text", text);
        ObjectNode voice = root.putObject("voice");
        voice.put("languageCode", languageCode);
        voice.put("name", voiceName);
        ObjectNode audioConfig = root.putObject("audioConfig");
        audioConfig.put("audioEncoding", "MP3");
        audioConfig.put("speakingRate", speechRate);
        return root;
    }

    private URI endpoint() {
        String baseUrl = StringUtils.hasText(properties.googleTtsBaseUrl())
                ? properties.googleTtsBaseUrl().trim()
                : "https://texttospeech.googleapis.com";
        return URI.create(baseUrl.replaceAll("/+$", "") + "/v1/text:synthesize");
    }

    private String projectId() {
        String projectId = properties.resolvedGoogleTtsProjectId();
        if (!StringUtils.hasText(projectId) || !projectId.matches("[A-Za-z0-9._-]+")) {
            throw new ExternalServiceUnavailableException("GOOGLE_TTS_PROJECT_ID 값이 올바르지 않습니다.");
        }
        return projectId.trim();
    }

    private static String applicationDefaultAccessToken() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of(CLOUD_PLATFORM_SCOPE));
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            if (token == null || !StringUtils.hasText(token.getTokenValue())) {
                token = credentials.refreshAccessToken();
            }
            if (token == null || !StringUtils.hasText(token.getTokenValue())) {
                throw new ExternalServiceUnavailableException("Google TTS access token을 발급받지 못했습니다.");
            }
            return token.getTokenValue();
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException(
                    "Google TTS Application Default Credentials를 찾을 수 없습니다.");
        }
    }
}
