package com.neulbom.backend.analysis.integration.aiserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AdministeredQuestionResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AudioResource;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.ResponseTiming;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.SttInput;
import com.neulbom.backend.config.AiServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiServerClientTest {

    @Test
    void restartAnalysisPostsFreshRequestToExistingAnalysisId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID analysisId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();
        server.expect(requestTo("http://ai.test/v1/analyses/" + analysisId + "/restart"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-secret"))
                .andExpect(header("Idempotency-Key", "analysis-restart-operation-1"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"analysis_id\":\"" + analysisId + "\"")))
                .andRespond(withSuccess("""
                        {"analysis_id":"%s","assessment_id":"%s","status":"pending","created_at":"2026-09-29T00:00:00Z"}
                        """.formatted(analysisId, assessmentId), MediaType.APPLICATION_JSON));
        var request = new AiServerContracts.AnalysisCreateRequest(
                analysisId, assessmentId, LocalDate.of(2026, 9, 29),
                new AiServerContracts.RecognitionPlanSnapshot(
                        new AiServerContracts.MemoryUnitMap(true, true, true, true, true), List.of()),
                List.of());

        var accepted = client(builder).restartAnalysis(
                analysisId, "analysis-restart-operation-1", request);

        assertThat(accepted.analysisId()).isEqualTo(analysisId);
        assertThat(accepted.status()).isEqualTo("pending");
        server.verify();
    }

    @Test
    void recognitionPlanUsesBearerTokenIdempotencyKeyAndSnakeCaseContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID assessmentId = UUID.randomUUID();
        UUID recordingId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        server.expect(requestTo("http://ai.test/v1/assessments/" + assessmentId + "/recognition-plan"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-secret"))
                .andExpect(header("Idempotency-Key", "recognition-plan-operation-1"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"question_set_version\":\"cist-v1\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"recording_id\":\"" + recordingId + "\"")))
                .andRespond(withSuccess("""
                        {
                          "assessment_id":"%s",
                          "status":"completed",
                          "question_set_version":"cist-v1",
                          "wrong_event_rule_version":"wrong-event-v1",
                          "recalled_units":{"person":true,"transport":false,"place":true,"time":false,"activity":true},
                          "next_question_codes":["memory_recognition_transport","memory_recognition_time"],
                          "q11_result":{
                            "question_code":"memory_delayed_free_recall",
                            "administration_status":"administered",
                            "recording_id":"%s",
                            "response_id":"%s",
                            "vad_status":"speech_detected",
                            "scoring_status":"not_scored",
                            "answer_status":null,
                            "wrong_event":0,
                            "wrong_event_reason":null,
                            "response_delay_ms":640,
                            "recognized_memory_units":{"person":true,"transport":false,"place":true,"time":false,"activity":true}
                          }
                        }
                        """.formatted(assessmentId, recordingId, responseId), MediaType.APPLICATION_JSON));

        AiServerClient client = client(builder);
        var request = new RecognitionPlanRequest(
                LocalDate.of(2026, 9, 5),
                new AdministeredQuestionResponse(
                        "memory_delayed_free_recall",
                        "memory-delayed-free-recall-fixed-v1",
                        recordingId,
                        responseId,
                        new AudioResource(
                                URI.create("https://audio.test/q11.wav?signature=redacted"),
                                Instant.parse("2026-09-05T10:30:00Z"),
                                "audio/wav",
                                1234,
                                null),
                        new SttInput("success", "민수는 공원에 가서 야구를 했다", "google-request-1"),
                        new ResponseTiming(180, 4250)));

        var result = client.createRecognitionPlan(assessmentId, "recognition-plan-operation-1", request);

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.nextQuestionCodes())
                .containsExactly("memory_recognition_transport", "memory_recognition_time");
        assertThat(result.q11Result().recordingId()).isEqualTo(recordingId);
        server.verify();
    }

    @Test
    void idempotencyConflictPreservesTheUpstreamErrorCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID analysisId = UUID.randomUUID();
        server.expect(requestTo("http://ai.test/v1/analyses/" + analysisId + "/retry"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"code":"IDEMPOTENCY_CONFLICT","message":"different body","retryable":false,"details":{}}}
                                """));

        AiServerClient client = client(builder);
        assertThatThrownBy(() -> client.retryAnalysis(
                analysisId,
                "analysis-retry-operation-1",
                new AiServerContracts.AnalysisRetryRequest("AUDIO_URL_EXPIRED", List.of())))
                .isInstanceOfSatisfying(AiServerException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.upstreamCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
                    assertThat(exception.retryable()).isFalse();
                });
        server.verify();
    }

    @Test
    void analysisStatusMapsThreeLevelRiskFields() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID analysisId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();
        var score = new BigDecimal("0.823");
        var features = new AiServerContracts.FusionFeatures(
                new BigDecimal("0.1"),
                new BigDecimal("0.2"),
                new BigDecimal("0.3"),
                new BigDecimal("0.4"));
        var questionResults = AiServerContractFixtures.fullQuestionResults();
        var finalResult = new AiServerContracts.FinalAnalysisResult(
                AiServerContracts.QUESTION_SET_VERSION,
                AiServerContracts.WRONG_EVENT_RULE_VERSION,
                AiServerContracts.FUSION_MODEL_VERSION,
                score,
                new BigDecimal("0.38592870327757767"),
                new BigDecimal("0.8061380697921943"),
                AiServerContracts.THRESHOLD_VERSION,
                true,
                "review_needed",
                features,
                AiServerContractFixtures.featureSnapshot(questionResults, score, features),
                questionResults);
        Instant createdAt = Instant.parse("2026-09-08T10:00:00Z");
        var response = new AiServerContracts.AnalysisStatusResponse(
                analysisId,
                assessmentId,
                "completed",
                createdAt,
                createdAt.plusSeconds(60),
                false,
                null,
                List.of(),
                finalResult);
        String responseBody = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(response);
        server.expect(requestTo("http://ai.test/v1/analyses/" + analysisId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        var result = client(builder).getAnalysis(analysisId);

        assertThat(result.result().decisionThreshold()).isEqualByComparingTo("0.38592870327757767");
        assertThat(result.result().reviewThreshold()).isEqualByComparingTo("0.8061380697921943");
        assertThat(result.result().thresholdVersion()).isEqualTo("fusion-threshold-v2");
        assertThat(result.result().riskFlag()).isTrue();
        assertThat(result.result().riskLevel()).isEqualTo("review_needed");
        assertThat(result.result().featureSnapshot().modelScore()).isEqualByComparingTo("0.823");
        server.verify();
    }

    @Test
    void analysisStatusMapsLegacyScoreWithoutFeatureSnapshot() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID analysisId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();
        var score = new BigDecimal("0.42");
        var result = new AiServerContracts.FinalAnalysisResult(
                AiServerContracts.QUESTION_SET_VERSION,
                AiServerContracts.WRONG_EVENT_RULE_VERSION,
                AiServerContracts.FUSION_MODEL_VERSION,
                score,
                new BigDecimal("0.38592870327757767"),
                new BigDecimal("0.8061380697921943"),
                AiServerContracts.THRESHOLD_VERSION,
                true,
                "monitoring_needed",
                new AiServerContracts.FusionFeatures(
                        new BigDecimal("0.1"), new BigDecimal("0.2"),
                        new BigDecimal("0.3"), new BigDecimal("0.4")),
                null,
                AiServerContractFixtures.fullQuestionResults());
        Instant now = Instant.parse("2026-09-29T10:00:00Z");
        var response = new AiServerContracts.AnalysisStatusResponse(
                analysisId, assessmentId, "completed", now, now, false, null, List.of(), result);
        String body = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(response);
        server.expect(requestTo("http://ai.test/v1/analyses/" + analysisId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var actual = client(builder).getAnalysis(analysisId);

        assertThat(actual.result().modelScore()).isEqualByComparingTo(score);
        assertThat(actual.result().featureSnapshot()).isNull();
        server.verify();
    }

    @Test
    void dailyCognitiveAnalysisUsesDedicatedCreateGetAndRetryPaths() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID analysisId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID baselineAnalysisId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-29T10:00:00Z");
        var snapshot = dailySnapshot();
        var responses = List.of(
                dailyResponse("orientation_year"),
                dailyResponse("attention_digit_span_4"));
        var request = new AiServerContracts.DailyAnalysisCreateRequest(
                analysisId,
                sessionId,
                baselineAnalysisId,
                LocalDate.of(2026, 9, 29),
                new BigDecimal("0.42"),
                snapshot,
                responses);

        server.expect(requestTo("http://ai.test/v1/daily-cognitive-analyses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-secret"))
                .andExpect(header("Idempotency-Key", "daily-analysis-create-operation-1"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"analysis_type\":\"daily_partial_update\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"input_snapshot\"")))
                .andRespond(withSuccess("""
                        {
                          "analysis_id":"%s",
                          "session_id":"%s",
                          "status":"pending",
                          "created_at":"%s"
                        }
                        """.formatted(analysisId, sessionId, createdAt), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ai.test/v1/daily-cognitive-analyses/" + analysisId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "analysis_id":"%s",
                          "session_id":"%s",
                          "status":"pending",
                          "created_at":"%s",
                          "updated_at":"%s",
                          "retryable":false,
                          "reason_code":null,
                          "retry_items":[],
                          "result":null
                        }
                        """.formatted(analysisId, sessionId, createdAt, createdAt), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ai.test/v1/daily-cognitive-analyses/" + analysisId + "/retry"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "daily-analysis-retry-operation-1"))
                .andRespond(withSuccess("""
                        {
                          "analysis_id":"%s",
                          "session_id":"%s",
                          "status":"pending",
                          "created_at":"%s"
                        }
                        """.formatted(analysisId, sessionId, createdAt), MediaType.APPLICATION_JSON));

        AiServerClient client = client(builder);
        var accepted = client.createDailyCognitiveAnalysis(
                "daily-analysis-create-operation-1",
                request);
        var status = client.getDailyCognitiveAnalysis(analysisId);
        var retried = client.retryDailyCognitiveAnalysis(
                analysisId,
                "daily-analysis-retry-operation-1",
                new AiServerContracts.AnalysisRetryRequest(
                        "AUDIO_URL_EXPIRED",
                        List.of(new AiServerContracts.ReissueAudioUrlItem(
                                "orientation_year",
                                responses.getFirst().recordingId(),
                                responses.getFirst().responseId(),
                                responses.getFirst().audio()))));

        assertThat(accepted.sessionId()).isEqualTo(sessionId);
        assertThat(status.status()).isEqualTo("pending");
        assertThat(retried.analysisId()).isEqualTo(analysisId);
        server.verify();
    }

    private AiServerContracts.AdministeredQuestionResponse dailyResponse(String questionCode) {
        return new AiServerContracts.AdministeredQuestionResponse(
                questionCode,
                questionCode + "-v1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new AudioResource(
                        URI.create("https://audio.test/" + questionCode + ".wav?signature=redacted"),
                        Instant.parse("2026-09-29T10:30:00Z"),
                        "audio/wav",
                        1234,
                        null),
                new SttInput("success", "테스트 응답", "google-request"),
                new ResponseTiming(100, 1000));
    }

    private AiServerContracts.CognitiveFeatureSnapshot dailySnapshot() {
        var features = new AiServerContracts.FusionFeatures(
                new BigDecimal("0.1"),
                new BigDecimal("0.2"),
                new BigDecimal("0.3"),
                new BigDecimal("0.4"));
        var results = List.of(
                dailyResult("orientation_year"),
                dailyResult("memory_registration_first"),
                dailyResult("attention_digit_span_4"),
                dailyResult("language_semantic_fluency"));
        return AiServerContractFixtures.featureSnapshot(
                results,
                new BigDecimal("0.42"),
                features);
    }

    private AiServerContracts.QuestionAnalysisResult dailyResult(String questionCode) {
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

    private AiServerClient client(RestClient.Builder builder) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new AiServerClient(
                builder.build(),
                new AiServerProperties(
                        true,
                        "http://ai.test",
                        "service-secret",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        0,
                        Duration.ofMinutes(30),
                        "https://backend.test",
                        "test-signing-secret-at-least-32-characters",
                        false),
                objectMapper);
    }
}
