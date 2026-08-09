package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

final class ProviderJson {

    private ProviderJson() {
    }

    static JsonNode data(JsonNode root) {
        JsonNode data = root == null ? null : root.get("data");
        return data != null && !data.isNull() ? data : root;
    }

    static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    static BigDecimal decimal(JsonNode node, String field, BigDecimal fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return fallback;
        }
        return value.decimalValue();
    }

    static JsonNode object(JsonNode node, String field, JsonNode fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value;
    }

    static String requiredText(JsonNode node, String field, String provider) {
        String value = text(node, field, null);
        if (value == null) {
            throw new com.neulbom.backend.common.exception.ExternalServiceUnavailableException(
                    provider + " provider 응답에 " + field + "가 없습니다.");
        }
        return value;
    }
}
