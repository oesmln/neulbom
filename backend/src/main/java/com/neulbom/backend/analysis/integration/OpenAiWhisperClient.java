package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiWhisperClient implements SpeechToTextClient {

    private final RestClient restClient;
    private final ExternalApiProperties properties;
    private final ExternalApiExecutor executor;

    public OpenAiWhisperClient(
            @Qualifier("externalRestClient") RestClient restClient,
            ExternalApiProperties properties,
            ExternalApiExecutor executor
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public boolean isConfigured() {
        return properties.whisperConfigured();
    }

    @Override
    public TranscriptionResult transcribe(AudioFile audioFile) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        ByteArrayResource resource = new ByteArrayResource(audioFile.content()) {
            @Override
            public String getFilename() {
                return StringUtils.hasText(audioFile.filename()) ? audioFile.filename() : "recording.wav";
            }
        };
        MediaType mediaType = mediaType(audioFile.contentType());
        body.part("file", resource).contentType(mediaType);
        body.part("model", model());
        body.part("language", "ko");
        body.part("response_format", "verbose_json");

        JsonNode response = executor.execute("Whisper", () -> restClient.post()
                .uri(ProviderUrls.resolve(properties.whisperBaseUrl(), "/v1/audio/transcriptions"))
                .header("Authorization", "Bearer " + properties.whisperApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(JsonNode.class));
        JsonNode payload = ProviderJson.data(response);
        String transcript = ProviderJson.requiredText(payload, "text", "Whisper").trim();
        if (transcript.isBlank()) {
            throw new com.neulbom.backend.common.exception.ExternalServiceUnavailableException(
                    "Whisper provider가 빈 전사 결과를 반환했습니다.");
        }
        return new TranscriptionResult(
                transcript,
                ProviderJson.decimal(payload, "duration", null),
                confidence(payload),
                ProviderJson.text(payload, "language", "ko"),
                ProviderJson.text(payload, "model", model()),
                model());
    }

    private String model() {
        return StringUtils.hasText(properties.whisperModel()) ? properties.whisperModel() : "whisper-1";
    }

    private MediaType mediaType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.parseMediaType("audio/wav");
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private BigDecimal confidence(JsonNode payload) {
        JsonNode segments = payload == null ? null : payload.get("segments");
        if (segments == null || !segments.isArray() || segments.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (JsonNode segment : segments) {
            JsonNode value = segment.get("avg_logprob");
            if (value != null && value.isNumber()) {
                double probability = Math.exp(value.asDouble());
                total = total.add(BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, probability))));
                count++;
            }
        }
        return count == 0 ? null : total.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP);
    }
}
