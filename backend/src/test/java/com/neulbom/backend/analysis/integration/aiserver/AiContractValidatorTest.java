package com.neulbom.backend.analysis.integration.aiserver;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisStatusResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.FinalAnalysisResult;
import com.neulbom.backend.common.exception.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class AiContractValidatorTest {

    private static final UUID ANALYSIS_ID = UUID.randomUUID();
    private static final UUID ASSESSMENT_ID = UUID.randomUUID();

    private final AiContractValidator validator = new AiContractValidator(
            new CistContractCatalog(new ObjectMapper()));

    @ParameterizedTest(name = "score={0} -> {2}")
    @CsvSource({
            "0.460999, false, stable",
            "0.461, true, monitoring_needed",
            "0.802, true, review_needed"
    })
    void validatesThreeLevelRiskBoundaries(String score, boolean riskFlag, String riskLevel) {
        AnalysisStatusResponse response = completedResponse(
                new BigDecimal(score), riskFlag, riskLevel);

        assertThatCode(() -> validator.validateAnalysisStatus(response, ANALYSIS_ID, ASSESSMENT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRiskLevelThatDoesNotMatchScore() {
        AnalysisStatusResponse response = completedResponse(
                new BigDecimal("0.61"), true, "review_needed");

        assertThatThrownBy(() -> validator.validateAnalysisStatus(response, ANALYSIS_ID, ASSESSMENT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("risk_level이 model_score 구간과 일치하지 않습니다.");
    }

    private AnalysisStatusResponse completedResponse(
            BigDecimal score,
            boolean riskFlag,
            String riskLevel
    ) {
        FinalAnalysisResult result = new FinalAnalysisResult(
                "cist-v1",
                "wrong-event-v1",
                "final_fusion_lr_21subjects_core4_ast_v1",
                score,
                new BigDecimal("0.461"),
                new BigDecimal("0.802"),
                "fusion-threshold-v2",
                riskFlag,
                riskLevel,
                null,
                IntStream.range(0, 17)
                        .mapToObj(index -> new AiServerContracts.QuestionAnalysisResult(
                                "question-" + index,
                                "not_applicable",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null))
                        .toList());
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        return new AnalysisStatusResponse(
                ANALYSIS_ID,
                ASSESSMENT_ID,
                "completed",
                now,
                now,
                false,
                null,
                List.of(),
                result);
    }
}
