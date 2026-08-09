package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
public class HttpAstAnalysisClient implements AcousticAnalysisClient {

    private final RestClient restClient;
    private final ExternalApiProperties properties;
    private final ExternalApiExecutor executor;

    public HttpAstAnalysisClient(
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
        return properties.astConfigured();
    }

    @Override
    public AcousticResult analyze(
            String recordingId,
            SpeechToTextClient.AudioFile audioFile,
            int segmentLengthSec,
            String modelVersion
    ) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        ByteArrayResource resource = new ByteArrayResource(audioFile.content()) {
            @Override
            public String getFilename() {
                return StringUtils.hasText(audioFile.filename()) ? audioFile.filename() : "recording.wav";
            }
        };
        body.part("audio_file", resource).contentType(mediaType(audioFile.contentType()));
        body.part("recording_id", recordingId);
        body.part("segment_length_sec", segmentLengthSec);
        body.part("model_version", version(modelVersion));

        JsonNode response = executor.execute("AST", () -> {
            var request = restClient.post()
                    .uri(ProviderUrls.resolve(properties.astApiUrl(), ""))
                    .contentType(MediaType.MULTIPART_FORM_DATA);
            if (StringUtils.hasText(properties.astApiKey())) {
                request.header("Authorization", "Bearer " + properties.astApiKey());
            }
            return request.body(body.build()).retrieve().body(JsonNode.class);
        });
        JsonNode payload = ProviderJson.data(response);
        BigDecimal referenceScore = ProviderJson.decimal(payload, "acoustic_reference_score", null);
        if (referenceScore == null) {
            throw new com.neulbom.backend.common.exception.ExternalServiceUnavailableException(
                    "AST provider 응답에 acoustic_reference_score가 없습니다.");
        }
        return new AcousticResult(
                referenceScore,
                ProviderJson.object(payload, "acoustic_flags", JsonNodeFactory.instance.objectNode()),
                ProviderJson.decimal(payload, "speech_rate", null),
                ProviderJson.decimal(payload, "pause_ratio", null),
                ProviderJson.decimal(payload, "energy_variability", null),
                ProviderJson.decimal(payload, "speech_stability", null),
                ProviderJson.text(payload, "model_name", "AST"),
                ProviderJson.text(payload, "model_version", version(modelVersion)));
    }

    private String version(String requested) {
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        return StringUtils.hasText(properties.astModel()) ? properties.astModel() : "v1";
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
