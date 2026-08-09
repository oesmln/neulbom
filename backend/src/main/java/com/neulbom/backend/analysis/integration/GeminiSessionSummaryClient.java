package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neulbom.backend.analysis.api.QaPair;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class GeminiSessionSummaryClient implements SessionSummaryClient {

    private final RestClient restClient;
    private final ExternalApiProperties properties;
    private final ExternalApiExecutor executor;
    private final ObjectMapper objectMapper;

    public GeminiSessionSummaryClient(
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
        return properties.geminiConfigured();
    }

    @Override
    public SummaryResult summarize(List<QaPair> qaPairs) {
        String model = model();
        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode contents = requestBody.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt(qaPairs));
        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("temperature", 0.2);
        generationConfig.put("responseMimeType", "application/json");

        JsonNode response = executor.execute("Gemini", () -> restClient.post()
                .uri(ProviderUrls.resolve(properties.geminiBaseUrl(),
                        "/v1beta/models/" + model + ":generateContent"))
                .header("x-goog-api-key", properties.geminiApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class));
        String generated = response == null ? null : response.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text").asText(null);
        if (!StringUtils.hasText(generated)) {
            throw new com.neulbom.backend.common.exception.ExternalServiceUnavailableException(
                    "Gemini provider 응답에 요약 텍스트가 없습니다.");
        }
        JsonNode structured = parseStructured(generated);
        String summary = structured != null && StringUtils.hasText(structured.path("summary").asText(null))
                ? structured.path("summary").asText().trim() : generated.trim();
        BigDecimal vocabularyScore = structured != null && structured.path("vocabulary_score").isNumber()
                ? structured.path("vocabulary_score").decimalValue() : vocabularyScore(qaPairs);
        List<String> keywords = keywords(structured);
        return new SummaryResult(summary, clampVocabulary(vocabularyScore), keywords, "Gemini", model);
    }

    private String prompt(List<QaPair> qaPairs) {
        StringBuilder builder = new StringBuilder();
        builder.append("고령자의 AI 정서 문답을 안전한 생활 기록으로 요약하세요. ")
                .append("의료적 진단, 치매 확정, 위험 확정 표현은 절대 사용하지 마세요. ")
                .append("JSON만 반환하고 스키마는 summary(문자열), vocabulary_score(0~100 숫자), keywords(문자열 배열)입니다.\n");
        for (int index = 0; index < qaPairs.size(); index++) {
            QaPair pair = qaPairs.get(index);
            builder.append(index + 1).append(". 질문: ").append(pair.question())
                    .append(" / 답변: ").append(pair.answer()).append('\n');
        }
        return builder.toString();
    }

    private JsonNode parseStructured(String generated) {
        String candidate = generated.trim();
        if (candidate.startsWith("```")) {
            candidate = candidate.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "").trim();
        }
        try {
            JsonNode parsed = objectMapper.readTree(candidate);
            return parsed != null && parsed.isObject() ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> keywords(JsonNode structured) {
        if (structured == null || !structured.path("keywords").isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        structured.path("keywords").forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank() && values.size() < 10) {
                values.add(value.asText().trim());
            }
        });
        return List.copyOf(values);
    }

    private BigDecimal vocabularyScore(List<QaPair> qaPairs) {
        long distinct = qaPairs.stream()
                .flatMap(pair -> java.util.Arrays.stream(pair.answer().split("\\s+")))
                .filter(value -> !value.isBlank())
                .distinct()
                .count();
        return BigDecimal.valueOf(Math.min(100, distinct * 10L)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clampVocabulary(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private String model() {
        return StringUtils.hasText(properties.geminiModel()) ? properties.geminiModel() : "gemini-2.5-flash";
    }
}
