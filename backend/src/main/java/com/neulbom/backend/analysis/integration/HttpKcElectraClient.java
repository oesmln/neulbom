package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class HttpKcElectraClient implements CognitiveAnalysisClient {

    private final RestClient restClient;
    private final ExternalApiProperties properties;
    private final ExternalApiExecutor executor;
    private final ObjectMapper objectMapper;

    public HttpKcElectraClient(
            @Qualifier("externalRestClient") RestClient restClient,
            ExternalApiProperties properties,
            ExternalApiExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return properties.kcElectraConfigured();
    }

    @Override
    public CognitiveResult analyze(String question, String transcript, String questionType, String modelVersion) {
        String version = version(modelVersion);
        var requestBody = objectMapper.createObjectNode();
        if (StringUtils.hasText(question)) {
            requestBody.put("question", question);
        }
        requestBody.put("transcript", transcript);
        requestBody.put("question_type", questionType);
        requestBody.put("model_version", version);

        JsonNode response = executor.execute("KcELECTRA", () -> {
            var request = restClient.post()
                    .uri(ProviderUrls.resolve(properties.kcElectraApiUrl(), ""))
                    .contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(properties.kcElectraApiKey())) {
                request.header("Authorization", "Bearer " + properties.kcElectraApiKey());
            }
            return request.body(requestBody).retrieve().body(JsonNode.class);
        });
        JsonNode payload = ProviderJson.data(response);
        BigDecimal languageScore = ProviderJson.decimal(payload, "language_reference_score", null);
        if (languageScore == null) {
            throw new com.neulbom.backend.common.exception.ExternalServiceUnavailableException(
                    "KcELECTRA provider 응답에 language_reference_score가 없습니다.");
        }
        return new CognitiveResult(
                languageScore,
                ProviderJson.object(payload, "cognitive_flags", JsonNodeFactory.instance.objectNode()),
                ProviderJson.object(payload, "domain_scores", JsonNodeFactory.instance.objectNode()),
                ProviderJson.object(payload, "model_breakdown", JsonNodeFactory.instance.objectNode()),
                ProviderJson.text(payload, "model_name", "KcELECTRA"),
                ProviderJson.text(payload, "model_version", version));
    }

    private String version(String requested) {
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        return StringUtils.hasText(properties.kcElectraModel()) ? properties.kcElectraModel() : "v1";
    }
}
