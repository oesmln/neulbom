package com.neulbom.backend.analysis.integration.aiserver;

import static com.neulbom.backend.analysis.integration.aiserver.AiServerContractFixtures.featureSnapshot;
import static com.neulbom.backend.analysis.integration.aiserver.AiServerContractFixtures.fullQuestionResults;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
            "0.385928, false, stable",
            "0.38592870327757767, true, monitoring_needed",
            "0.8061380697921943, true, review_needed"
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

    @Test
    void validatesDailyPartialUpdateRequestAndCompletedResult() {
        var createRequest = dailyCreateRequest(
                List.of(
                        dailyResponse("orientation_year"),
                        dailyResponse("attention_digit_span_4")));
        var statusResponse = dailyCompletedResponse(
                new BigDecimal("0.2"),
                new BigDecimal("0.1"));

        assertThatCode(() -> validator.validateDailyAnalysisCreate(createRequest))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateDailyAnalysisStatus(
                statusResponse,
                ANALYSIS_ID,
                ASSESSMENT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDailyRequestWithoutOrientationAndAttentionPair() {
        var request = dailyCreateRequest(
                List.of(
                        dailyResponse("orientation_year"),
                        dailyResponse("orientation_month")));

        assertThatThrownBy(() -> validator.validateDailyAnalysisCreate(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("지남력 1문항과 주의력 1문항");
    }

    @Test
    void rejectsDailyResultWithInconsistentDelta() {
        var response = dailyCompletedResponse(
                new BigDecimal("0.3"),
                new BigDecimal("0.1"));

        assertThatThrownBy(() -> validator.validateDailyAnalysisStatus(
                response,
                ANALYSIS_ID,
                ASSESSMENT_ID))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("기준 점수 대비 변화량이 일치하지 않습니다.");
    }

    private AnalysisStatusResponse completedResponse(
            BigDecimal score,
            boolean riskFlag,
            String riskLevel
    ) {
        var features = new AiServerContracts.FusionFeatures(
                new BigDecimal("0.1"),
                new BigDecimal("0.2"),
                new BigDecimal("0.3"),
                new BigDecimal("0.4"));
        var questionResults = fullQuestionResults();
        FinalAnalysisResult result = new FinalAnalysisResult(
                "cist-v1",
                "wrong-event-v1",
                AiServerContracts.FUSION_MODEL_VERSION,
                score,
                new BigDecimal("0.38592870327757767"),
                new BigDecimal("0.8061380697921943"),
                "fusion-threshold-v2",
                riskFlag,
                riskLevel,
                features,
                featureSnapshot(questionResults, score, features),
                questionResults);
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

    private AiServerContracts.DailyAnalysisCreateRequest dailyCreateRequest(
            List<AiServerContracts.AdministeredQuestionResponse> responses
    ) {
        var features = features();
        var snapshot = featureSnapshot(
                fullQuestionResults(),
                new BigDecimal("0.5"),
                features);
        return new AiServerContracts.DailyAnalysisCreateRequest(
                ANALYSIS_ID,
                ASSESSMENT_ID,
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 29),
                new BigDecimal("0.4"),
                snapshot,
                responses);
    }

    private AiServerContracts.DailyAnalysisStatusResponse dailyCompletedResponse(
            BigDecimal deltaFromBaseline,
            BigDecimal deltaFromPrevious
    ) {
        var features = features();
        var outputSnapshot = featureSnapshot(
                fullQuestionResults(),
                new BigDecimal("0.6"),
                features);
        var questionResults = List.of(
                questionResult("orientation_year"),
                questionResult("attention_digit_span_4"));
        var result = new AiServerContracts.DailyAnalysisResult(
                "daily_partial_estimate",
                UUID.randomUUID(),
                new BigDecimal("0.4"),
                new BigDecimal("0.5"),
                new BigDecimal("0.6"),
                deltaFromBaseline,
                deltaFromPrevious,
                AiServerContracts.FUSION_MODEL_VERSION,
                new BigDecimal("0.38592870327757767"),
                new BigDecimal("0.8061380697921943"),
                AiServerContracts.THRESHOLD_VERSION,
                true,
                "monitoring_needed",
                List.of("orientation_year", "attention_digit_span_4"),
                features,
                outputSnapshot,
                questionResults);
        Instant now = Instant.parse("2026-09-29T10:00:00Z");
        return new AiServerContracts.DailyAnalysisStatusResponse(
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

    private AiServerContracts.AdministeredQuestionResponse dailyResponse(String questionCode) {
        return new AiServerContracts.AdministeredQuestionResponse(
                questionCode,
                questionCode + "-v1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new AiServerContracts.AudioResource(
                        URI.create("https://audio.test/" + questionCode + ".wav?signature=test"),
                        Instant.parse("2026-09-29T10:30:00Z"),
                        "audio/wav",
                        1234,
                        null),
                new AiServerContracts.SttInput("success", "테스트 응답", "google-request"),
                new AiServerContracts.ResponseTiming(100, 1000));
    }

    private AiServerContracts.QuestionAnalysisResult questionResult(String questionCode) {
        return new AiServerContracts.QuestionAnalysisResult(
                questionCode,
                "administered",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "speech_detected",
                "scored",
                "correct",
                0,
                null,
                500L,
                null);
    }

    private AiServerContracts.FusionFeatures features() {
        return new AiServerContracts.FusionFeatures(
                new BigDecimal("0.1"),
                new BigDecimal("0.2"),
                new BigDecimal("0.3"),
                new BigDecimal("0.4"));
    }
}
