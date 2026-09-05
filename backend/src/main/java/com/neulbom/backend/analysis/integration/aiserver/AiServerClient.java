package com.neulbom.backend.analysis.integration.aiserver;

import java.net.URI;
import java.util.UUID;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisAcceptedResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisCreateRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisRetryRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisStatusResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.ErrorResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanResponse;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.AiServerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiServerClient {

    private final RestClient restClient;
    private final AiServerProperties properties;
    private final ObjectMapper objectMapper;

    public AiServerClient(
            @Qualifier("aiServerRestClient") RestClient restClient,
            AiServerProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return properties.configured();
    }

    public RecognitionPlanResponse createRecognitionPlan(
            UUID assessmentId,
            String idempotencyKey,
            RecognitionPlanRequest body
    ) {
        return execute(() -> post(
                "/v1/assessments/" + assessmentId + "/recognition-plan",
                idempotencyKey,
                body,
                RecognitionPlanResponse.class));
    }

    public AnalysisAcceptedResponse createAnalysis(String idempotencyKey, AnalysisCreateRequest body) {
        return execute(() -> post("/v1/analyses", idempotencyKey, body, AnalysisAcceptedResponse.class));
    }

    public AnalysisStatusResponse getAnalysis(UUID analysisId) {
        return execute(() -> restClient.get()
                .uri(uri("/v1/analyses/" + analysisId))
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AnalysisStatusResponse.class));
    }

    public AnalysisAcceptedResponse retryAnalysis(
            UUID analysisId,
            String idempotencyKey,
            AnalysisRetryRequest body
    ) {
        return execute(() -> post(
                "/v1/analyses/" + analysisId + "/retry",
                idempotencyKey,
                body,
                AnalysisAcceptedResponse.class));
    }

    private <T> T post(String path, String idempotencyKey, Object body, Class<T> responseType) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency-Key가 필요합니다.");
        }
        return restClient.post()
                .uri(uri(path))
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    private <T> T execute(Supplier<T> request) {
        requireConfigured();
        int attempts = Math.max(1, properties.retryCount() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                T response = request.get();
                if (response == null) {
                    throw new ExternalServiceUnavailableException("AI 서버가 빈 응답을 반환했습니다.");
                }
                return response;
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().is5xxServerError() && attempt < attempts) {
                    continue;
                }
                throw mapError(exception);
            } catch (RestClientException exception) {
                if (attempt == attempts) {
                    throw new ExternalServiceUnavailableException("AI 서버에 연결할 수 없습니다.");
                }
            }
        }
        throw new ExternalServiceUnavailableException("AI 서버에 연결할 수 없습니다.");
    }

    private AiServerException mapError(RestClientResponseException exception) {
        ErrorResponse response = null;
        try {
            response = objectMapper.readValue(exception.getResponseBodyAsByteArray(), ErrorResponse.class);
        } catch (Exception ignored) {
            // Upstream body may be empty or malformed. Never include it in backend logs or responses.
        }
        String code = response != null && response.error() != null
                ? response.error().code() : "AI_SERVER_ERROR";
        String message = response != null && response.error() != null && StringUtils.hasText(response.error().message())
                ? response.error().message() : "AI 서버 요청이 거부되었습니다.";
        boolean retryable = response != null && response.error() != null && response.error().retryable();
        HttpStatus status = switch (exception.getStatusCode().value()) {
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return new AiServerException(status, code, message, retryable);
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ExternalServiceUnavailableException("AI 서버 주소 또는 서비스 토큰이 설정되지 않았습니다.");
        }
    }

    private String bearerToken() {
        return "Bearer " + properties.serviceToken();
    }

    private URI uri(String path) {
        String base = properties.baseUrl().trim();
        if (base.endsWith("/") && path.startsWith("/")) {
            return URI.create(base.substring(0, base.length() - 1) + path);
        }
        return URI.create(base + path);
    }
}
