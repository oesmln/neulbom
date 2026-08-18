package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calculates the two metadata features used by the CIST fusion model.
 *
 * <p>The calculation intentionally keeps the four categories balanced. A
 * category with more recordings cannot dominate the subject-level feature.
 * Response times are stored by the backend in milliseconds, while the
 * exported feature is expressed in seconds.</p>
 */
public final class CistMetadataFeatureCalculator {

    public static final List<String> CORE_CATEGORIES = List.of(
            "orientation", "memory", "attention", "language");

    private static final Set<String> EXPLICIT_WRONG_PHRASES = Set.of(
            "틀린 답", "틀린 대답", "오답", "모른다고", "모름", "무응답");
    private static final Set<String> NON_WRONG_PHRASES = Set.of(
            "주변 소음", "기침", "검사자 목소리", "겹침", "녹음 오류");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);

    private CistMetadataFeatureCalculator() {
    }

    public static FeatureVector calculate(List<MetadataEvent> events) {
        Objects.requireNonNull(events, "events");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("CIST 메타데이터 이벤트가 없습니다.");
        }

        Map<String, Integer> wrongCounts = new LinkedHashMap<>();
        Map<String, List<Integer>> delaysByCategory = new LinkedHashMap<>();
        for (String category : CORE_CATEGORIES) {
            wrongCounts.put(category, 0);
            delaysByCategory.put(category, new ArrayList<>());
        }

        for (MetadataEvent event : events) {
            Objects.requireNonNull(event, "event");
            String category = canonicalCategory(event.category());
            if (!CORE_CATEGORIES.contains(category)) {
                throw new IllegalArgumentException("지원하지 않는 CIST 질문범주: " + event.category());
            }
            if (event.responseTimeMs() != null && event.responseTimeMs() < 0) {
                throw new IllegalArgumentException("응답 지연시간은 음수일 수 없습니다.");
            }
            if (isExplicitWrong(event)) {
                wrongCounts.compute(category, (ignored, count) -> count + 1);
            }
            if (event.responseTimeMs() != null) {
                delaysByCategory.get(category).add(event.responseTimeMs());
            }
        }

        Set<String> presentCategories = events.stream()
                .map(event -> canonicalCategory(event.category()))
                .collect(Collectors.toSet());
        if (!presentCategories.containsAll(CORE_CATEGORIES)) {
            Set<String> missing = CORE_CATEGORIES.stream()
                    .filter(category -> !presentCategories.contains(category))
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            String displayMissing = missing.stream()
                    .map(category -> "language".equals(category) ? "language(유창성)" : category)
                    .collect(Collectors.joining(", ", "[", "]"));
            throw new IllegalArgumentException("핵심 CIST 질문범주 누락: " + displayMissing);
        }

        Map<String, CategoryMetrics> categories = new LinkedHashMap<>();
        BigDecimal wrongScoreSum = BigDecimal.ZERO;
        BigDecimal delaySum = BigDecimal.ZERO;
        boolean hasDelayForEveryCategory = true;
        for (String category : CORE_CATEGORIES) {
            int cappedWrongCount = Math.min(wrongCounts.get(category), 2);
            BigDecimal wrongScore = BigDecimal.valueOf(cappedWrongCount)
                    .divide(TWO, 6, RoundingMode.HALF_UP);
            BigDecimal medianDelaySeconds = medianSeconds(delaysByCategory.get(category));
            categories.put(category, new CategoryMetrics(
                    wrongCounts.get(category), cappedWrongCount, wrongScore, medianDelaySeconds));
            wrongScoreSum = wrongScoreSum.add(wrongScore);
            if (medianDelaySeconds == null) {
                hasDelayForEveryCategory = false;
            } else {
                delaySum = delaySum.add(medianDelaySeconds);
            }
        }

        BigDecimal balancedWrongScore = wrongScoreSum
                .divide(FOUR, 6, RoundingMode.HALF_UP);
        BigDecimal balancedDelay = hasDelayForEveryCategory
                ? delaySum.divide(FOUR, 6, RoundingMode.HALF_UP)
                : null;
        return new FeatureVector(balancedWrongScore, balancedDelay,
                Collections.unmodifiableMap(categories));
    }

    public static String canonicalCategory(String category) {
        if (category == null) {
            return "";
        }
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "orientation", "지남력" -> "orientation";
            case "memory", "기억력" -> "memory";
            case "attention", "주의력" -> "attention";
            case "language", "fluency", "유창성" -> "language";
            default -> category.trim().toLowerCase(Locale.ROOT);
        };
    }

    public static boolean isExplicitWrongNote(String note) {
        if (note == null || note.isBlank()) {
            return false;
        }
        String normalized = note.replaceAll("\\s+", " ").trim();
        if (NON_WRONG_PHRASES.stream().anyMatch(normalized::contains)) {
            return false;
        }
        return EXPLICIT_WRONG_PHRASES.stream().anyMatch(normalized::contains);
    }

    private static boolean isExplicitWrong(MetadataEvent event) {
        return event.explicitWrongEvent() == null
                ? isExplicitWrongNote(event.note())
                : event.explicitWrongEvent();
    }

    private static BigDecimal medianSeconds(List<Integer> responseTimesMs) {
        if (responseTimesMs.isEmpty()) {
            return null;
        }
        List<Integer> sorted = responseTimesMs.stream().sorted().toList();
        int middle = sorted.size() / 2;
        BigDecimal medianMs;
        if (sorted.size() % 2 == 0) {
            medianMs = BigDecimal.valueOf(sorted.get(middle - 1))
                    .add(BigDecimal.valueOf(sorted.get(middle)))
                    .divide(TWO, 6, RoundingMode.HALF_UP);
        } else {
            medianMs = BigDecimal.valueOf(sorted.get(middle));
        }
        return medianMs.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    public record MetadataEvent(
            String category,
            Boolean explicitWrongEvent,
            String note,
            Integer responseTimeMs
    ) {
    }

    public record CategoryMetrics(
            int rawWrongEventCount,
            int cappedWrongEventCount,
            BigDecimal wrongEventScore,
            BigDecimal medianDelaySeconds
    ) {
    }

    public record FeatureVector(
            BigDecimal categoryBalancedWrongEventScore,
            BigDecimal categoryBalancedMedianDelay,
            Map<String, CategoryMetrics> categories
    ) {
    }
}
