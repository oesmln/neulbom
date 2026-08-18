package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CistRubricEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluatesExactAndKeywordRubricsWithoutUsingRiskScoreAsCorrectness() throws Exception {
        var exact = CistRubricEvaluator.evaluate(
                "exact_text", objectMapper.readTree("[\"화요일\"]"), BigDecimal.ONE, "화요일입니다.");
        var keyword = CistRubricEvaluator.evaluate(
                "contains_keyword", objectMapper.readTree("[\"가족\", \"친구\"]"), BigDecimal.ONE, "오늘 가족을 만났어요.");

        assertThat(exact.status()).isEqualTo("completed");
        assertThat(exact.correct()).isFalse();
        assertThat(keyword.status()).isEqualTo("completed");
        assertThat(keyword.correct()).isTrue();
        assertThat(keyword.score()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void leavesManualRubricUnevaluated() throws Exception {
        var result = CistRubricEvaluator.evaluate(
                "manual", objectMapper.readTree("[]"), BigDecimal.ONE, "답변");

        assertThat(result.status()).isEqualTo("unsupported");
        assertThat(result.correct()).isNull();
        assertThat(result.score()).isNull();
    }
}
