package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/** Applies only the deterministic part of a versioned CIST rubric. */
public final class CistRubricEvaluator {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\s]");

    private CistRubricEvaluator() {
    }

    public static Result evaluate(
            String ruleType,
            JsonNode expectedValues,
            BigDecimal maxScore,
            String transcript
    ) {
        Objects.requireNonNull(ruleType, "ruleType");
        Objects.requireNonNull(maxScore, "maxScore");
        if ("manual".equalsIgnoreCase(ruleType) || expectedValues == null || expectedValues.isNull()) {
            return new Result("unsupported", null, null);
        }
        List<String> expected = values(expectedValues);
        if (expected.isEmpty()) {
            return new Result("unsupported", null, null);
        }

        String answer = normalize(transcript);
        boolean correct = switch (ruleType.toLowerCase(Locale.ROOT)) {
            case "exact_text" -> expected.stream().map(CistRubricEvaluator::normalize).anyMatch(answer::equals);
            case "contains_keyword" -> expected.stream().map(CistRubricEvaluator::normalize)
                    .filter(value -> !value.isBlank())
                    .anyMatch(answer::contains);
            default -> false;
        };
        if (!"exact_text".equalsIgnoreCase(ruleType)
                && !"contains_keyword".equalsIgnoreCase(ruleType)) {
            return new Result("unsupported", null, null);
        }
        return new Result("completed", correct, correct ? maxScore : BigDecimal.ZERO);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return NON_WORD.matcher(value)
                .replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static List<String> values(JsonNode expectedValues) {
        List<String> values = new ArrayList<>();
        if (expectedValues.isArray()) {
            expectedValues.forEach(value -> {
                if (value.isTextual() && !value.asText().isBlank()) {
                    values.add(value.asText());
                }
            });
        } else if (expectedValues.isObject() && expectedValues.path("values").isArray()) {
            expectedValues.path("values").forEach(value -> {
                if (value.isTextual() && !value.asText().isBlank()) {
                    values.add(value.asText());
                }
            });
        }
        return values;
    }

    public record Result(String status, Boolean correct, BigDecimal score) {
    }
}
