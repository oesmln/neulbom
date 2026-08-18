package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class CistMetadataFeatureCalculatorTest {

    @Test
    void capsWrongEventsPerCategoryAndAveragesFourCategoriesEqually() {
        var events = List.of(
                event("orientation", true, 1210),
                event("orientation", true, 1300),
                event("orientation", true, 1400),
                event("orientation", true, 1500),
                event("orientation", true, 1600),
                event("memory", false, 580),
                event("attention", true, 940),
                event("language", false, 1800));

        var result = CistMetadataFeatureCalculator.calculate(events);

        assertThat(result.categoryBalancedWrongEventScore()).isEqualByComparingTo("0.375000");
        assertThat(result.categories().get("orientation").rawWrongEventCount()).isEqualTo(5);
        assertThat(result.categories().get("orientation").cappedWrongEventCount()).isEqualTo(2);
    }

    @Test
    void includesZeroResponseTimeAndCalculatesCategoryMedianInSeconds() {
        var events = List.of(
                event("orientation", false, 0),
                event("orientation", false, 2000),
                event("memory", false, 580),
                event("attention", false, 940),
                event("language", false, 1800));

        var result = CistMetadataFeatureCalculator.calculate(events);

        assertThat(result.categories().get("orientation").medianDelaySeconds())
                .isEqualByComparingTo("1.000000");
        assertThat(result.categoryBalancedMedianDelay()).isEqualByComparingTo("1.080000");
    }

    @Test
    void ignoresNoiseNotesButRecognizesExplicitWrongNotes() {
        assertThat(CistMetadataFeatureCalculator.isExplicitWrongNote("틀린 답")).isTrue();
        assertThat(CistMetadataFeatureCalculator.isExplicitWrongNote("무응답")).isTrue();
        assertThat(CistMetadataFeatureCalculator.isExplicitWrongNote("주변 소음으로 녹음 오류")).isFalse();
        assertThat(CistMetadataFeatureCalculator.isExplicitWrongNote("검사자 목소리 겹침")).isFalse();
    }

    @Test
    void rejectsNegativeResponseTimeAndMissingCategory() {
        assertThatThrownBy(() -> CistMetadataFeatureCalculator.calculate(List.of(
                event("orientation", false, -1),
                event("memory", false, 1),
                event("attention", false, 1),
                event("language", false, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");

        assertThatThrownBy(() -> CistMetadataFeatureCalculator.calculate(List.of(
                event("orientation", false, 1),
                event("memory", false, 1),
                event("attention", false, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유창성");
    }

    private CistMetadataFeatureCalculator.MetadataEvent event(
            String category, boolean wrong, int responseTimeMs) {
        return new CistMetadataFeatureCalculator.MetadataEvent(
                category, wrong, null, responseTimeMs);
    }
}
