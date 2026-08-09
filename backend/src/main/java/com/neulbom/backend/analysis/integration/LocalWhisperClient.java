package com.neulbom.backend.analysis.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Calls a locally hosted Whisper server that exposes the OpenAI-compatible
 * {@code POST /v1/audio/transcriptions} contract.
 */
@Component
public class LocalWhisperClient implements SpeechToTextClient {

    private final RestClient restClient;
    private final ExternalApiProperties properties;
    private final ExternalApiExecutor executor;

    public LocalWhisperClient(
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
        return properties.localWhisperConfigured();
    }

    @Override
    public TranscriptionResult transcribe(AudioFile audioFile) {
        if (!isConfigured()) {
            throw new ExternalServiceUnavailableException("로컬 Whisper 서버 URL이 설정되지 않았습니다.");
        }

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        ByteArrayResource resource = new ByteArrayResource(audioFile.content()) {
            @Override
            public String getFilename() {
                return StringUtils.hasText(audioFile.filename()) ? audioFile.filename() : "recording.wav";
            }
        };
        body.part("file", resource).contentType(mediaType(audioFile.contentType()));
        body.part("model", model());
        body.part("language", "ko");
        body.part("response_format", "verbose_json");

        JsonNode response = executor.execute("Local Whisper", () -> {
            var request = restClient.post()
                    .uri(ProviderUrls.resolve(properties.localWhisperBaseUrl(), "/v1/audio/transcriptions"))
                    .contentType(MediaType.MULTIPART_FORM_DATA);
            if (StringUtils.hasText(properties.localWhisperApiKey())) {
                request.header("Authorization", "Bearer " + properties.localWhisperApiKey());
            }
            return request.body(body.build()).retrieve().body(JsonNode.class);
        });
        return WhisperResponseMapper.map(response, "Local Whisper", model());
    }

    private String model() {
        return StringUtils.hasText(properties.localWhisperModel())
                ? properties.localWhisperModel() : "whisper-1";
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
}
