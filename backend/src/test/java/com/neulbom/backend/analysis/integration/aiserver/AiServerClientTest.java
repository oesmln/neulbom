package com.neulbom.backend.analysis.integration.aiserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
                        "test-signing-secret-at-least-32-characters"),
                objectMapper);
    }
}
