package com.neulbom.backend.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.analysis.integration.aiserver.AiAudioUrlSigner;
import com.neulbom.backend.analysis.integration.aiserver.AiServerClient;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisCreateRequest;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.recording.TranscriptEntity;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.session.AnswerEntity;
import com.neulbom.backend.session.AnswerRepository;
import com.neulbom.backend.session.QuestionEntity;
import com.neulbom.backend.session.QuestionRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CistAiAnalysisIntegrationTest {

    private static final Set<String> SELECTED = Set.of(
            "memory_recognition_transport",
            "memory_recognition_time");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private RecordingRepository recordingRepository;
    @Autowired private TranscriptRepository transcriptRepository;
    @Autowired private AnswerRepository answerRepository;
    @Autowired private CistAiAnalysisRepository analysisRepository;

    @MockitoBean private AiServerClient aiServerClient;
    @MockitoBean private AiAudioUrlSigner audioUrlSigner;

    @Test
    void recognitionPlanAndSeventeenQuestionAnalysisFollowTheIntegratedContract() throws Exception {
        Instant now = Instant.parse("2026-09-05T09:00:00Z");
        UserEntity elder = userRepository.save(new UserEntity(
                UUID.randomUUID(),
                "cist-ai-" + UUID.randomUUID() + "@example.com",
                null,
                "통합 분석 테스트",
                "elder",
                LocalDate.of(1945, 1, 1),
                "80s_plus",
                "female",
                null,
                true,
                now,
                now));
        SessionEntity session = sessionRepository.save(new SessionEntity(
                UUID.randomUUID(), elder.getId(), "cist", 17, "{}", false, now));

        List<QuestionEntity> questions = questionRepository
                .findAllByActiveTrueAndSessionTypeOrderByDisplayOrderAsc("cist");
        for (QuestionEntity question : questions) {
            if ("conditional".equals(question.getAdministrationMode())
                    && !SELECTED.contains(question.getQuestionCode())) {
                continue;
            }
            saveAdministeredResponse(elder, session, question, now.plusSeconds(question.getDisplayOrder()));
        }

        when(audioUrlSigner.issue(any(RecordingEntity.class))).thenAnswer(invocation -> {
            RecordingEntity recording = invocation.getArgument(0);
            return new AiServerContracts.AudioResource(
                    URI.create("https://audio.test/" + recording.getId() + "?signature=test"),
                    now.plusSeconds(3600),
                    "audio/wav",
                    recording.getFileSizeBytes(),
                    null);
        });
        when(aiServerClient.createRecognitionPlan(any(UUID.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    UUID assessmentId = invocation.getArgument(0);
                    AiServerContracts.RecognitionPlanRequest request = invocation.getArgument(2);
                    var units = new AiServerContracts.MemoryUnitMap(true, false, true, false, true);
                    var q11 = request.response();
                    return new AiServerContracts.RecognitionPlanResponse(
                            assessmentId,
                            "completed",
                            "cist-v1",
                            "wrong-event-v1",
                            units,
                            List.copyOf(SELECTED),
                            new AiServerContracts.QuestionAnalysisResult(
                                    q11.questionCode(), "administered", q11.recordingId(), q11.responseId(),
                                    "speech_detected", "not_scored", null, 0, null, 500L, units),
                            null,
                            false,
                            List.of());
                });
        when(aiServerClient.createAnalysis(anyString(), any(AnalysisCreateRequest.class)))
                .thenAnswer(invocation -> {
                    AnalysisCreateRequest request = invocation.getArgument(1);
                    return new AiServerContracts.AnalysisAcceptedResponse(
                            request.analysisId(), request.assessmentId(), "pending", now.plusSeconds(30));
                });

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/cist-ai/recognition-plan", session.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(elder.getId().toString()).claim("role", "elder"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.assessment_id").value(session.getId().toString()))
                .andExpect(jsonPath("$.next_question_codes.length()").value(2));

        org.assertj.core.api.Assertions.assertThat(
                sessionRepository.findById(session.getId()).orElseThrow().getTotalQuestions()).isEqualTo(14);

        session.end(now.plusSeconds(60));
        sessionRepository.save(session);

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/cist-ai/analyses", session.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(elder.getId().toString()).claim("role", "elder"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.session_id").value(session.getId().toString()));

        ArgumentCaptor<AnalysisCreateRequest> captor = ArgumentCaptor.forClass(AnalysisCreateRequest.class);
        verify(aiServerClient).createAnalysis(anyString(), captor.capture());
        AnalysisCreateRequest request = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.responses()).hasSize(17);
        org.assertj.core.api.Assertions.assertThat(request.responses().stream()
                        .filter(response -> "not_applicable".equals(response.administrationStatus()))
                        .map(AiServerContracts.QuestionResponseInput::questionCode))
                .containsExactlyInAnyOrder(
                        "memory_recognition_person",
                        "memory_recognition_place",
                        "memory_recognition_activity");
        org.assertj.core.api.Assertions.assertThat(request.analysisId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(request.assessmentId()).isEqualTo(session.getId());

        when(aiServerClient.getAnalysis(request.analysisId())).thenReturn(new AiServerContracts.AnalysisStatusResponse(
                request.analysisId(),
                session.getId(),
                "needs_retry",
                now.plusSeconds(30),
                now.plusSeconds(40),
                true,
                "AUDIO_DOWNLOAD_FAILED",
                List.of(
                        new AiServerContracts.RetryItem(
                                "orientation_year", "AUDIO_URL_EXPIRED", "REISSUE_AUDIO_URL"),
                        new AiServerContracts.RetryItem(
                                "memory_delayed_free_recall", "UNSCORABLE_STT", "REPLACE_RESPONSE")),
                null));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/cist-ai/analyses", session.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(elder.getId().toString()).claim("role", "elder"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("needs_retry"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.retry_items.length()").value(2));

        QuestionEntity q11 = questionRepository.findByQuestionCodeAndActiveTrue("memory_delayed_free_recall")
                .orElseThrow();
        saveAdministeredResponse(elder, session, q11, now.plusSeconds(120));
        when(aiServerClient.retryAnalysis(any(UUID.class), anyString(), any()))
                .thenReturn(new AiServerContracts.AnalysisAcceptedResponse(
                        request.analysisId(), session.getId(), "pending", now.plusSeconds(50)));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/cist-ai/analyses/retry", session.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(elder.getId().toString()).claim("role", "elder"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.retry_count").value(1));

        ArgumentCaptor<AiServerContracts.AnalysisRetryRequest> retryCaptor =
                ArgumentCaptor.forClass(AiServerContracts.AnalysisRetryRequest.class);
        verify(aiServerClient).retryAnalysis(
                org.mockito.ArgumentMatchers.eq(request.analysisId()), anyString(), retryCaptor.capture());
        AiServerContracts.AnalysisRetryRequest retryRequest = retryCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(retryRequest.items()).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(retryRequest.items())
                .anyMatch(AiServerContracts.ReissueAudioUrlItem.class::isInstance)
                .anyMatch(AiServerContracts.ReplaceResponseItem.class::isInstance);

        AiServerContracts.ReplaceResponseItem replacement = retryRequest.items().stream()
                .filter(AiServerContracts.ReplaceResponseItem.class::isInstance)
                .map(AiServerContracts.ReplaceResponseItem.class::cast)
                .findFirst()
                .orElseThrow();
        List<AiServerContracts.QuestionAnalysisResult> finalQuestionResults = new ArrayList<>();
        for (AiServerContracts.QuestionResponseInput response : request.responses()) {
            if (response instanceof AiServerContracts.NotApplicableQuestionResponse) {
                finalQuestionResults.add(new AiServerContracts.QuestionAnalysisResult(
                        response.questionCode(), "not_applicable", null, null,
                        null, null, null, null, null, null, null));
            } else {
                AiServerContracts.AdministeredQuestionResponse administered =
                        (AiServerContracts.AdministeredQuestionResponse) response;
                UUID recordingId = administered.recordingId();
                UUID responseId = administered.responseId();
                if ("memory_delayed_free_recall".equals(response.questionCode())) {
                    recordingId = replacement.recordingId();
                    responseId = replacement.responseId();
                }
                finalQuestionResults.add(new AiServerContracts.QuestionAnalysisResult(
                        response.questionCode(), "administered", recordingId, responseId,
                        "speech_detected", "scored", "correct", 0, null, 250L, null));
            }
        }
        var finalResult = new AiServerContracts.FinalAnalysisResult(
                "cist-v1",
                "wrong-event-v1",
                "final_fusion_lr_21subjects_core4_ast_v1",
                new BigDecimal("0.61"),
                new BigDecimal("0.5"),
                "fusion-threshold-v1",
                true,
                new AiServerContracts.FusionFeatures(
                        new BigDecimal("0.1"),
                        new BigDecimal("0.2"),
                        new BigDecimal("0.3"),
                        new BigDecimal("0.4")),
                finalQuestionResults);
        when(aiServerClient.getAnalysis(request.analysisId())).thenReturn(new AiServerContracts.AnalysisStatusResponse(
                request.analysisId(), session.getId(), "completed", now.plusSeconds(30), now.plusSeconds(70),
                false, null, List.of(), finalResult));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/cist-ai/analyses", session.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(elder.getId().toString()).claim("role", "elder"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.model_score").value(0.61))
                .andExpect(jsonPath("$.model_version").value("final_fusion_lr_21subjects_core4_ast_v1"))
                .andExpect(jsonPath("$.decision_threshold").value(0.5))
                .andExpect(jsonPath("$.threshold_version").value("fusion-threshold-v1"))
                .andExpect(jsonPath("$.risk_flag").value(true));

        CistAiAnalysisEntity stored = analysisRepository.findById(request.analysisId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getModelScore()).isEqualByComparingTo("0.61");
        org.assertj.core.api.Assertions.assertThat(stored.getModelVersion())
                .isEqualTo("final_fusion_lr_21subjects_core4_ast_v1");
    }

    private void saveAdministeredResponse(
            UserEntity elder,
            SessionEntity session,
            QuestionEntity question,
            Instant answeredAt
    ) {
        UUID recordingId = UUID.randomUUID();
        UUID transcriptId = UUID.randomUUID();
        RecordingEntity recording = new RecordingEntity(
                recordingId,
                UUID.randomUUID(),
                elder.getId(),
                RecordingEntity.ANSWER,
                session.getId(),
                question.getId(),
                "recordings/" + recordingId + ".wav",
                "answer.wav",
                "{}",
                "audio/wav",
                1024,
                4000,
                answeredAt,
                answeredAt);
        recordingRepository.save(recording);
        transcriptRepository.save(new TranscriptEntity(
                transcriptId,
                recordingId,
                "민수는 공원에 가서 야구를 했습니다.",
                new BigDecimal("4.000"),
                new BigDecimal("0.95"),
                "ko-KR",
                "chirp_3",
                "v2",
                "completed",
                answeredAt,
                answeredAt,
                answeredAt));
        answerRepository.save(new AnswerEntity(
                UUID.randomUUID(),
                session.getId(),
                question.getId(),
                UUID.randomUUID(),
                null,
                recordingId,
                transcriptId,
                250,
                answeredAt,
                answeredAt));
    }
}
