package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.databind.JsonNode;

final class WhisperResponseMapper {

    private WhisperResponseMapper() {
    }

    static SpeechToTextClient.TranscriptionResult map(
            JsonNode response,
            String provider,
            String defaultModel
    ) {
        JsonNode payload = ProviderJson.data(response);
        String transcript = ProviderJson.requiredText(payload, "text", provider).trim();
        if (transcript.isBlank()) {
            throw new com.neulbom.backend.common.exception.ExternalServiceUnavailableException(
                    provider + " provider가 빈 전사 결과를 반환했습니다.");
        }
        return new SpeechToTextClient.TranscriptionResult(
                transcript,
                ProviderJson.decimal(payload, "duration", null),
                confidence(payload),
                ProviderJson.text(payload, "language", "ko"),
                ProviderJson.text(payload, "model", defaultModel),
                defaultModel);
    }

    private static BigDecimal confidence(JsonNode payload) {
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
        return count == 0 ? null : total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }
}
