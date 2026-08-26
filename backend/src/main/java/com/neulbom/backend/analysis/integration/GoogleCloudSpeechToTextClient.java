package com.neulbom.backend.analysis.integration;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Base64;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.common.exception.EmptyTranscriptException;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Google Cloud Speech-to-Text V2 adapter using Application Default Credentials.
 * The V2 auto-decoding contract supports the audio containers used by the app.
 */
@Component
public class GoogleCloudSpeechToTextClient implements SpeechToTextClient {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final RestClient restClient;
    private final ExternalApiProperties properties;
    private final ExternalApiExecutor executor;
    private final ObjectMapper objectMapper;
    private final Supplier<String> accessTokenSupplier;

    @Autowired
    public GoogleCloudSpeechToTextClient(
            @Qualifier("externalRestClient") RestClient restClient,
            ExternalApiProperties properties,
            ExternalApiExecutor executor,
            ObjectMapper objectMapper
    ) {
        this(restClient, properties, executor, objectMapper,
                GoogleCloudSpeechToTextClient::applicationDefaultAccessToken);
    }

    GoogleCloudSpeechToTextClient(
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
        return properties.googleSttConfigured();
    }

    @Override
    public TranscriptionResult transcribe(AudioFile audioFile) {
        if (!isConfigured()) {
            throw new ExternalServiceUnavailableException("Google STT 프로젝트 ID가 설정되지 않았습니다.");
        }

        JsonNode response = executor.execute("Google STT", () -> restClient.post()
                .uri(endpoint())
                .header("Authorization", "Bearer " + accessTokenSupplier.get())
                .header("x-goog-user-project", properties.googleSttProjectId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request(audioFile))
                .retrieve()
                .body(JsonNode.class));
        return mapResponse(response);
    }

    private ObjectNode request(AudioFile audioFile) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode config = root.putObject("config");
        config.putObject("autoDecodingConfig");
        ArrayNode languages = config.putArray("languageCodes");
        languages.add(languageCode());
        config.put("model", model());
        config.putObject("features").put(
                "enableAutomaticPunctuation",
                properties.googleSttAutomaticPunctuation());
        root.put("content", Base64.getEncoder().encodeToString(audioFile.content()));
        return root;
    }

    private TranscriptionResult mapResponse(JsonNode response) {
        JsonNode results = response == null ? null : response.get("results");
        if (results == null || !results.isArray()) {
            throw new ExternalServiceUnavailableException("Google STT 응답에 results가 없습니다.");
        }

        StringBuilder transcript = new StringBuilder();
        BigDecimal confidenceTotal = BigDecimal.ZERO;
        int confidenceCount = 0;
        String language = languageCode();
        for (JsonNode result : results) {
            JsonNode alternatives = result.get("alternatives");
            if (alternatives == null || !alternatives.isArray() || alternatives.isEmpty()) {
                continue;
            }
            JsonNode alternative = alternatives.get(0);
            String text = alternative.path("transcript").asText("").trim();
            if (!text.isBlank()) {
                if (transcript.length() > 0) {
                    transcript.append(' ');
                }
                transcript.append(text);
            }
            JsonNode confidence = alternative.get("confidence");
            if (confidence != null && confidence.isNumber()) {
                confidenceTotal = confidenceTotal.add(confidence.decimalValue());
                confidenceCount++;
            }
            if (StringUtils.hasText(result.path("languageCode").asText())) {
                language = result.path("languageCode").asText();
            }
        }
        if (transcript.isEmpty()) {
            throw new EmptyTranscriptException();
        }
        BigDecimal confidence = confidenceCount == 0
                ? null : confidenceTotal.divide(BigDecimal.valueOf(confidenceCount), 4, java.math.RoundingMode.HALF_UP);
        return new TranscriptionResult(
                transcript.toString(),
                billedDuration(response),
                confidence,
                language,
                model(),
                "v2");
    }

    private BigDecimal billedDuration(JsonNode response) {
        String value = response == null ? "" : response.path("metadata")
                .path("totalBilledDuration").asText("");
        if (!value.endsWith("s")) {
            return null;
        }
        try {
            return new BigDecimal(value.substring(0, value.length() - 1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private URI endpoint() {
        String projectId = segment(properties.googleSttProjectId(), "GOOGLE_STT_PROJECT_ID");
        String location = segment(location(), "GOOGLE_STT_LOCATION");
        String baseUrl = StringUtils.hasText(properties.googleSttBaseUrl())
                ? properties.googleSttBaseUrl().trim()
                : "global".equals(location)
                        ? "https://speech.googleapis.com"
                        : "https://" + location + "-speech.googleapis.com";
        return ProviderUrls.resolve(baseUrl,
                "/v2/projects/" + projectId + "/locations/" + location + "/recognizers/_:recognize");
    }

    private String location() {
        return StringUtils.hasText(properties.googleSttLocation())
                ? properties.googleSttLocation().trim() : "us";
    }

    private String model() {
        return StringUtils.hasText(properties.googleSttModel())
                ? properties.googleSttModel().trim() : "chirp_3";
    }

    private String languageCode() {
        return StringUtils.hasText(properties.googleSttLanguageCode())
                ? properties.googleSttLanguageCode().trim() : "ko-KR";
    }

    private String segment(String value, String propertyName) {
        if (!StringUtils.hasText(value) || !value.matches("[A-Za-z0-9._-]+")) {
            throw new ExternalServiceUnavailableException(propertyName + " 값이 올바르지 않습니다.");
        }
        return value.trim();
    }

    private static String applicationDefaultAccessToken() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(java.util.List.of(CLOUD_PLATFORM_SCOPE));
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            if (token == null || !StringUtils.hasText(token.getTokenValue())) {
                token = credentials.refreshAccessToken();
            }
            if (token == null || !StringUtils.hasText(token.getTokenValue())) {
                throw new ExternalServiceUnavailableException("Google STT access token을 발급받지 못했습니다.");
            }
            return token.getTokenValue();
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException(
                    "Google STT Application Default Credentials를 찾을 수 없습니다.");
        }
    }
}
