package com.neulbom.backend.analysis;

import static com.neulbom.backend.analysis.integration.aiserver.AiServerContractFixtures.featureSnapshot;
import static com.neulbom.backend.analysis.integration.aiserver.AiServerContractFixtures.fullQuestionResults;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.analysis.integration.aiserver.AiAudioUrlSigner;
import com.neulbom.backend.analysis.integration.aiserver.AiServerClient;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.DailyAnalysisCreateRequest;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.recording.TranscriptEntity;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.session.AnswerEntity;
import com.neulbom.backend.session.AnswerRepository;
import com.neulbom.backend.session.QuestionEntity;
import com.neulbom.backend.session.QuestionRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionQuestionSlotEntity;
import com.neulbom.backend.session.SessionQuestionSlotRepository;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import com.neulbom.backend.user.ConsentEntity;
import com.neulbom.backend.user.ConsentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DailyCognitiveAnalysisIntegrationTest {

    @Autowired private CistAiAnalysisService analysisService;
    @Autowired private CistAiAnalysisRepository analysisRepository;
    @Autowired private CognitiveFeatureSnapshotService baselineSnapshotService;
    @Autowired private CognitiveFeatureSnapshotRepository baselineSnapshotRepository;
    @Autowired private DailyCognitiveEstimateService dailyEstimateService;
    @Autowired private DailyCognitiveEstimateRepository dailyEstimateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SessionQuestionSlotRepository slotRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private RecordingRepository recordingRepository;
    @Autowired private TranscriptRepository transcriptRepository;
    @Autowired private AnswerRepository answerRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;

    @MockitoBean private AiServerClient aiServerClient;
    @MockitoBean private AiAudioUrlSigner audioUrlSigner;

    @Test
    void createsDailyPartialAnalysisFromLatestSnapshotAndPersistsCompletedStatus() throws Exception {
        Instant baselineStartedAt = Instant.parse("2026-09-20T00:00:00Z");
        Instant baselineEndedAt = baselineStartedAt.plusSeconds(600);
        Instant previousDailyStartedAt = baselineEndedAt.plusSeconds(3600);
        Instant previousDailyEndedAt = previousDailyStartedAt.plusSeconds(600);
        Instant currentDailyStartedAt = previousDailyEndedAt.plusSeconds(3600);
        Instant now = currentDailyStartedAt.plusSeconds(3600);
        UserEntity elder = userRepository.save(new UserEntity(
                UUID.randomUUID(), "daily-analysis-" + UUID.randomUUID() + "@example.com", null,
                "일상 분석 테스트", "elder", LocalDate.of(1945, 1, 1), "80s_plus", "female", null,
                true, baselineStartedAt, baselineStartedAt));
        saveDailyAnalysisConsents(elder.getId(), baselineStartedAt);

        SessionEntity baselineSession = endedSession(elder.getId(), "cist", 17, baselineStartedAt, baselineEndedAt);
        SessionEntity previousDailySession = endedSession(
                elder.getId(), "emotional_qa", 7, previousDailyStartedAt, previousDailyEndedAt);
        SessionEntity currentDailySession = endedSession(
                elder.getId(), "emotional_qa", 7, currentDailyStartedAt, now);
        sessionRepository.saveAll(List.of(baselineSession, previousDailySession, currentDailySession));

        AiServerContracts.FusionFeatures baselineFeatures = features("0.10", "0.20", "0.30", "0.40");
        var baselineSnapshot = featureSnapshot(fullQuestionResults(), new BigDecimal("0.41"), baselineFeatures);
        CistAiAnalysisEntity baselineAnalysis = completedAnalysis(
                baselineSession.getId(), null, "0.41", baselineSnapshot, baselineEndedAt);
        analysisRepository.save(baselineAnalysis);
        CognitiveFeatureSnapshotEntity storedBaseline = baselineSnapshotService.saveBaselineSnapshot(
                elder.getId(), baselineSession.getId(), baselineAnalysis.getAnalysisId(),
                "cist-v1", AiServerContracts.FUSION_MODEL_VERSION, AiServerContracts.THRESHOLD_VERSION,
                new BigDecimal("0.41"), objectMapper.writeValueAsString(baselineSnapshot));

        AiServerContracts.FusionFeatures previousFeatures = features("0.11", "0.21", "0.31", "0.41");
        var previousSnapshot = featureSnapshot(fullQuestionResults(), new BigDecimal("0.52"), previousFeatures);
        CistAiAnalysisEntity previousAnalysis = completedAnalysis(
                previousDailySession.getId(), baselineAnalysis.getAnalysisId(), "0.52", previousSnapshot,
                now.minusSeconds(200));
        analysisRepository.save(previousAnalysis);
        DailyCognitiveEstimateEntity previousEstimate = dailyEstimateService.createDailyEstimate(
                elder.getId(), previousDailySession.getId(), storedBaseline.getSnapshotId());
        dailyEstimateService.completeDailyEstimate(previousEstimate.getEstimateId(), new DailyEstimateCompletion(
                new BigDecimal("0.52"), new BigDecimal("0.11"), AiServerContracts.FUSION_MODEL_VERSION,
                AiServerContracts.THRESHOLD_VERSION, "monitoring_needed", "{}",
                Instant.now().plusSeconds(30), objectMapper.writeValueAsString(previousSnapshot)));

        QuestionEntity orientationSource = questionRepository.findByQuestionCodeAndActiveTrue("orientation_year")
                .orElseThrow();
        QuestionEntity attentionSource = questionRepository.findByQuestionCodeAndActiveTrue("attention_digit_span_4")
                .orElseThrow();
        for (int order : List.of(1, 3, 4, 5, 7)) {
            slotRepository.save(new SessionQuestionSlotEntity(
                    currentDailySession.getId(), order, "gemini", null));
        }
        List<AdministeredFixture> administered = new ArrayList<>();
        administered.add(addDailyResponse(elder, currentDailySession, orientationSource, 2, now));
        administered.add(addDailyResponse(elder, currentDailySession, attentionSource, 6, now.plusSeconds(20)));
        when(audioUrlSigner.issue(any(RecordingEntity.class))).thenAnswer(invocation -> {
            RecordingEntity recording = invocation.getArgument(0);
            return new AiServerContracts.AudioResource(
                    URI.create("https://audio.test/" + recording.getId()),
                    now.plusSeconds(3600),
                    "audio/wav",
                    recording.getFileSizeBytes(),
                    null);
        });
        when(aiServerClient.createDailyCognitiveAnalysis(anyString(), any(DailyAnalysisCreateRequest.class)))
                .thenAnswer(invocation -> {
                    DailyAnalysisCreateRequest request = invocation.getArgument(1);
                    return new AiServerContracts.DailyAnalysisAcceptedResponse(
                            request.analysisId(), request.sessionId(), "pending", now);
                });

        var accepted = analysisService.createDailyAnalysis(elder.getId(), currentDailySession.getId());
        assertThat(accepted.status()).isEqualTo("pending");
        assertThat(accepted.sessionId()).isEqualTo(currentDailySession.getId());
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/cist-ai/daily-analyses", currentDailySession.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(elder.getId().toString()).claim("role", "elder"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.model_score").doesNotExist())
                .andExpect(jsonPath("$.result").doesNotExist());

        ArgumentCaptor<DailyAnalysisCreateRequest> createCaptor =
                ArgumentCaptor.forClass(DailyAnalysisCreateRequest.class);
        verify(aiServerClient).createDailyCognitiveAnalysis(anyString(), createCaptor.capture());
        DailyAnalysisCreateRequest request = createCaptor.getValue();
        assertThat(request.baselineAnalysisId()).isEqualTo(baselineAnalysis.getAnalysisId());
        assertThat(request.baselineModelScore()).isEqualByComparingTo("0.41");
        assertThat(request.inputSnapshot().modelScore()).isEqualByComparingTo("0.52");
        DailyCognitiveEstimateEntity currentEstimate = dailyEstimateRepository
                .findBySessionId(currentDailySession.getId()).orElseThrow();
        assertThat(currentEstimate.getBaselineSnapshotId()).isEqualTo(storedBaseline.getSnapshotId());
        assertThat(currentEstimate.getParentEstimateId()).isEqualTo(previousEstimate.getEstimateId());
        assertThat(request.responses()).extracting(AiServerContracts.AdministeredQuestionResponse::questionCode)
                .containsExactly("orientation_year", "attention_digit_span_4");
        assertThat(request.responses()).extracting(AiServerContracts.AdministeredQuestionResponse::responseId)
                .containsExactly(administered.get(0).answerId(), administered.get(1).answerId());
        assertThat(request.responses()).allSatisfy(response ->
                assertThat(response.audio().signedUrl().toString()).startsWith("https://audio.test/"));

        when(aiServerClient.getDailyCognitiveAnalysis(request.analysisId()))
                .thenReturn(new AiServerContracts.DailyAnalysisStatusResponse(
                        request.analysisId(), currentDailySession.getId(), "processing", now, now.plusSeconds(5),
                        false, null, List.of(), null));
        assertThat(analysisService.refreshDailyAnalysis(elder.getId(), currentDailySession.getId()).status())
                .isEqualTo("processing");
        assertThat(dailyEstimateRepository.findById(currentEstimate.getEstimateId()).orElseThrow().getStatus())
                .isEqualTo("processing");

        when(aiServerClient.getDailyCognitiveAnalysis(request.analysisId()))
                .thenReturn(new AiServerContracts.DailyAnalysisStatusResponse(
                        request.analysisId(), currentDailySession.getId(), "needs_retry", now, now.plusSeconds(10),
                        true, "UNSCORABLE_STT",
                        List.of(new AiServerContracts.RetryItem(
                                "orientation_year", "UNSCORABLE_STT", "REPLACE_RESPONSE")),
                        null));
        assertThat(analysisService.refreshDailyAnalysis(elder.getId(), currentDailySession.getId()).status())
                .isEqualTo("needs_retry");
        assertThat(dailyEstimateRepository.findById(currentEstimate.getEstimateId()).orElseThrow().getStatus())
                .isEqualTo("processing");

        AdministeredFixture orientation = administered.get(0);
        QuestionEntity dailyQuestion = questionRepository.findByIdAndSessionIdAndActiveTrue(
                slotRepository.findAllBySessionIdOrderByQuestionOrderAsc(currentDailySession.getId()).stream()
                        .filter(slot -> "cist_bank".equals(slot.getQuestionSource())
                                && slot.getSourceQuestionId() != null)
                        .filter(slot -> "orientation_year".equals(questionRepository.findById(
                                slot.getSourceQuestionId()).map(QuestionEntity::getQuestionCode).orElse(null)))
                        .findFirst().orElseThrow().getQuestionId(),
                currentDailySession.getId()).orElseThrow();
        UUID replacementRecordingId = UUID.randomUUID();
        UUID replacementTranscriptId = UUID.randomUUID();
        recordingRepository.save(new RecordingEntity(
                replacementRecordingId, UUID.randomUUID(), elder.getId(), RecordingEntity.ANSWER,
                currentDailySession.getId(), dailyQuestion.getId(), "daily/" + replacementRecordingId + ".wav",
                "replacement.wav", "{}", "audio/wav", 2048, 4200, now.plusSeconds(40), now.plusSeconds(40)));
        transcriptRepository.save(new TranscriptEntity(
                replacementTranscriptId, replacementRecordingId, "새 답변 전사문", new BigDecimal("4.200"),
                BigDecimal.ONE, "ko", "Google STT", "chirp_3", "completed",
                now.plusSeconds(40), now.plusSeconds(40), now.plusSeconds(40)));
        Instant replacementAnsweredAt = Instant.now().minusSeconds(1);
        var replacementResponse = mockMvc.perform(post("/api/v1/sessions/{sessionId}/answers", currentDailySession.getId())
                        .with(jwt().jwt(jwt -> jwt.subject(elder.getId().toString()).claim("role", "elder")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client_answer_id":"%s",
                                  "question_id":"%s",
                                  "recording_id":"%s",
                                  "transcript_id":"%s",
                                  "response_time_ms":900,
                                  "answered_at":"%s"
                                }
                                """.formatted(
                                UUID.randomUUID(), dailyQuestion.getId(), replacementRecordingId,
                                replacementTranscriptId, replacementAnsweredAt)))
                .andReturn().getResponse();
        assertThat(replacementResponse.getStatus())
                .as("replacement response body: " + replacementResponse.getContentAsString())
                .isEqualTo(201);
        when(audioUrlSigner.issue(any(RecordingEntity.class))).thenAnswer(invocation -> {
            RecordingEntity recording = invocation.getArgument(0);
            return new AiServerContracts.AudioResource(
                    URI.create("https://audio.test/" + recording.getId()),
                    now.plusSeconds(3600), "audio/wav", recording.getFileSizeBytes(), null);
        });
        when(aiServerClient.retryDailyCognitiveAnalysis(eq(request.analysisId()), anyString(), any()))
                .thenReturn(new AiServerContracts.DailyAnalysisAcceptedResponse(
                        request.analysisId(), currentDailySession.getId(), "pending", now.plusSeconds(20)));
        assertThat(analysisService.retryDailyAnalysis(elder.getId(), currentDailySession.getId()).retryCount())
                .isEqualTo(1);
        ArgumentCaptor<AiServerContracts.AnalysisRetryRequest> retryCaptor =
                ArgumentCaptor.forClass(AiServerContracts.AnalysisRetryRequest.class);
        verify(aiServerClient).retryDailyCognitiveAnalysis(eq(request.analysisId()), anyString(), retryCaptor.capture());
        var replacement = retryCaptor.getValue().items().stream()
                .filter(AiServerContracts.ReplaceResponseItem.class::isInstance)
                .map(AiServerContracts.ReplaceResponseItem.class::cast)
                .findFirst().orElseThrow();
        assertThat(replacement.questionCode()).isEqualTo(orientation.questionCode());
        assertThat(replacement.recordingId()).isEqualTo(replacementRecordingId);

        AiServerContracts.FusionFeatures finalFeatures = features("0.12", "0.22", "0.32", "0.42");
        var outputSnapshot = featureSnapshot(fullQuestionResults(), new BigDecimal("0.55"), finalFeatures);
        List<AiServerContracts.QuestionAnalysisResult> questionResults = List.of(
                new AiServerContracts.QuestionAnalysisResult(
                        orientation.questionCode(), "administered", replacement.recordingId(), replacement.responseId(),
                        "speech_detected", "scored", "correct", 0, null, 500L, null),
                new AiServerContracts.QuestionAnalysisResult(
                        administered.get(1).questionCode(), "administered", administered.get(1).recordingId(),
                        administered.get(1).answerId(), "speech_detected", "scored", "correct", 0, null, 500L, null));
        var dailyResult = new AiServerContracts.DailyAnalysisResult(
                "daily_partial_estimate", baselineAnalysis.getAnalysisId(), new BigDecimal("0.41"),
                new BigDecimal("0.52"), new BigDecimal("0.55"), new BigDecimal("0.14"), new BigDecimal("0.03"),
                AiServerContracts.FUSION_MODEL_VERSION,
                new BigDecimal("0.38592870327757767"), new BigDecimal("0.8061380697921943"),
                AiServerContracts.THRESHOLD_VERSION, true, "monitoring_needed",
                List.of("orientation_year", "attention_digit_span_4"), finalFeatures, outputSnapshot, questionResults);
        when(aiServerClient.getDailyCognitiveAnalysis(request.analysisId()))
                .thenReturn(new AiServerContracts.DailyAnalysisStatusResponse(
                        request.analysisId(), currentDailySession.getId(), "completed", now,
                        Instant.now().plusSeconds(30),
                        false, null, List.of(), dailyResult));

        var synchronizedResult = analysisService.refreshDailyAnalysis(elder.getId(), currentDailySession.getId());
        assertThat(synchronizedResult.status()).isEqualTo("completed");
        assertThat(synchronizedResult.modelScore()).isEqualByComparingTo("0.55");
        assertThat(synchronizedResult.riskLevel()).isEqualTo("monitoring_needed");
        CistAiAnalysisEntity stored = analysisRepository.findBySessionId(currentDailySession.getId()).orElseThrow();
        assertThat(stored.getBaselineAnalysisId()).isEqualTo(baselineAnalysis.getAnalysisId());
        assertThat(stored.getFeatureSnapshot()).contains("\"model_score\":0.55");
        DailyCognitiveEstimateEntity completedEstimate = dailyEstimateRepository
                .findBySessionId(currentDailySession.getId()).orElseThrow();
        assertThat(completedEstimate.getStatus()).isEqualTo("completed");
        assertThat(completedEstimate.getEstimatedModelScore()).isEqualByComparingTo("0.55");
        assertThat(completedEstimate.getScoreDelta()).isEqualByComparingTo("0.14");
        assertThat(completedEstimate.getResultJson()).contains("\"result_type\":\"daily_partial_estimate\"");
        assertThat(completedEstimate.getOutputFeatureSnapshot()).contains("\"model_score\":0.55");
        assertThat(analysisService.refreshDailyAnalysis(elder.getId(), currentDailySession.getId()).status())
                .isEqualTo("completed");
        assertThat(dailyEstimateRepository.findBySessionId(currentDailySession.getId()).orElseThrow().getEstimateId())
                .isEqualTo(completedEstimate.getEstimateId());

        analysisService.createDailyAnalysis(elder.getId(), currentDailySession.getId());
        verify(aiServerClient, times(1)).createDailyCognitiveAnalysis(anyString(), any(DailyAnalysisCreateRequest.class));
    }

    @Test
    void carriesForwardCompletedSnapshotsAndStartsNewLineageAfterRepeatCist() throws Exception {
        Instant start = Instant.parse("2026-09-20T00:00:00Z");
        UserEntity elder = userRepository.save(new UserEntity(
                UUID.randomUUID(), "daily-lineage-" + UUID.randomUUID() + "@example.com", null,
                "일상 분석 계보 테스트", "elder", LocalDate.of(1945, 1, 1), "80s_plus", "female", null,
                true, start, start));
        saveDailyAnalysisConsents(elder.getId(), start);

        SessionEntity firstCist = endedSession(elder.getId(), "cist", 17, start, start.plusSeconds(600));
        SessionEntity firstDaily = endedSession(elder.getId(), "emotional_qa", 7,
                start.plusSeconds(3600), start.plusSeconds(4200));
        SessionEntity secondDaily = endedSession(elder.getId(), "emotional_qa", 7,
                start.plusSeconds(7200), start.plusSeconds(7800));
        SessionEntity repeatCist = endedSession(elder.getId(), "cist", 17,
                start.plusSeconds(10800), start.plusSeconds(11400));
        SessionEntity thirdDaily = endedSession(elder.getId(), "emotional_qa", 7,
                start.plusSeconds(14400), start.plusSeconds(15000));
        sessionRepository.saveAll(List.of(firstCist, firstDaily, secondDaily, repeatCist, thirdDaily));

        var features = features("0.10", "0.20", "0.30", "0.40");
        CistAiAnalysisEntity firstBaseline = completedAnalysis(firstCist.getId(), null, "0.41",
                featureSnapshot(fullQuestionResults(), new BigDecimal("0.41"), features), start.plusSeconds(600));
        analysisRepository.save(firstBaseline);
        prepareDailyResponses(elder, firstDaily, start.plusSeconds(4200));
        prepareDailyResponses(elder, secondDaily, start.plusSeconds(7800));
        prepareDailyResponses(elder, thirdDaily, start.plusSeconds(15000));
        when(audioUrlSigner.issue(any(RecordingEntity.class))).thenAnswer(invocation -> {
            RecordingEntity recording = invocation.getArgument(0);
            return new AiServerContracts.AudioResource(
                    URI.create("https://audio.test/" + recording.getId()),
                    Instant.parse("2099-01-01T00:00:00Z"), "audio/wav", recording.getFileSizeBytes(), null);
        });
        when(aiServerClient.createDailyCognitiveAnalysis(anyString(), any(DailyAnalysisCreateRequest.class)))
                .thenAnswer(invocation -> {
                    DailyAnalysisCreateRequest request = invocation.getArgument(1);
                    return new AiServerContracts.DailyAnalysisAcceptedResponse(
                            request.analysisId(), request.sessionId(), "pending", start);
                });

        var firstRequest = createAndCaptureDailyRequest(elder.getId(), firstDaily.getId(), 1);
        assertThat(firstRequest.baselineAnalysisId()).isEqualTo(firstBaseline.getAnalysisId());
        assertThat(firstRequest.inputSnapshot().modelScore()).isEqualByComparingTo("0.41");
        DailyCognitiveEstimateEntity firstEstimate = dailyEstimateRepository
                .findBySessionId(firstDaily.getId()).orElseThrow();
        assertThat(firstEstimate.getParentEstimateId()).isNull();
        completeDailyRequest(elder.getId(), firstRequest, "0.45", start.plusSeconds(4300));

        var secondRequest = createAndCaptureDailyRequest(elder.getId(), secondDaily.getId(), 2);
        assertThat(secondRequest.baselineAnalysisId()).isEqualTo(firstBaseline.getAnalysisId());
        assertThat(secondRequest.baselineModelScore()).isEqualByComparingTo("0.41");
        assertThat(secondRequest.inputSnapshot().modelScore()).isEqualByComparingTo("0.45");
        assertThat(dailyEstimateRepository.findBySessionId(secondDaily.getId()).orElseThrow().getParentEstimateId())
                .isEqualTo(firstEstimate.getEstimateId());
        completeDailyRequest(elder.getId(), secondRequest, "0.48", start.plusSeconds(7900));

        CistAiAnalysisEntity newBaseline = completedAnalysis(repeatCist.getId(), null, "0.36",
                featureSnapshot(fullQuestionResults(), new BigDecimal("0.36"), features), start.plusSeconds(11400));
        analysisRepository.save(newBaseline);

        var thirdRequest = createAndCaptureDailyRequest(elder.getId(), thirdDaily.getId(), 3);
        assertThat(thirdRequest.baselineAnalysisId()).isEqualTo(newBaseline.getAnalysisId());
        assertThat(thirdRequest.baselineModelScore()).isEqualByComparingTo("0.36");
        assertThat(thirdRequest.inputSnapshot().modelScore()).isEqualByComparingTo("0.36");
        DailyCognitiveEstimateEntity thirdEstimate = dailyEstimateRepository
                .findBySessionId(thirdDaily.getId()).orElseThrow();
        assertThat(thirdEstimate.getParentEstimateId()).isNull();
        assertThat(thirdEstimate.getBaselineSnapshotId())
                .isEqualTo(baselineSnapshotRepository.findBySourceAnalysisId(newBaseline.getAnalysisId())
                        .orElseThrow().getSnapshotId());
        assertThat(analysisRepository.findById(firstBaseline.getAnalysisId()).orElseThrow().getFeatureSnapshot())
                .contains("\"model_score\":0.41");
        assertThat(analysisRepository.findBySessionId(firstDaily.getId()).orElseThrow().getFeatureSnapshot())
                .contains("\"model_score\":0.45");
        assertThat(analysisRepository.findBySessionId(secondDaily.getId()).orElseThrow().getFeatureSnapshot())
                .contains("\"model_score\":0.48");
        when(aiServerClient.getDailyCognitiveAnalysis(thirdRequest.analysisId()))
                .thenReturn(new AiServerContracts.DailyAnalysisStatusResponse(
                        thirdRequest.analysisId(), thirdDaily.getId(), "failed", start,
                        Instant.now().plusSeconds(30), false, "MODEL_UNAVAILABLE", List.of(), null));
        assertThat(analysisService.refreshDailyAnalysis(elder.getId(), thirdDaily.getId()).status())
                .isEqualTo("failed");
        assertThat(dailyEstimateRepository.findBySessionId(thirdDaily.getId()).orElseThrow().getStatus())
                .isEqualTo("failed");
    }

    @Test
    void rejectsDailyAnalysisWhenLatestConsentIsNotAgreed() {
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        UserEntity elder = userRepository.save(new UserEntity(
                UUID.randomUUID(), "daily-consent-" + UUID.randomUUID() + "@example.com", null,
                "일상 분석 동의 테스트", "elder", LocalDate.of(1945, 1, 1), "80s_plus", "female", null,
                true, now, now));
        SessionEntity session = endedSession(elder.getId(), "emotional_qa", 7, now, now.plusSeconds(60));
        sessionRepository.save(session);
        consentRepository.save(new ConsentEntity(
                UUID.randomUUID(), elder.getId(), "analysis", false, null, "v2", now.plusSeconds(30)));
        consentRepository.save(new ConsentEntity(
                UUID.randomUUID(), elder.getId(), "voice_collection", true, now, "v1", now));

        assertThatThrownBy(() -> analysisService.createDailyAnalysis(elder.getId(), session.getId()))
                .hasMessageContaining("인지 활동 분석 동의가 필요합니다.");
    }

    private void saveDailyAnalysisConsents(UUID userId, Instant now) {
        consentRepository.save(new ConsentEntity(
                UUID.randomUUID(), userId, "analysis", true, now, "v1", now));
        consentRepository.save(new ConsentEntity(
                UUID.randomUUID(), userId, "voice_collection", true, now, "v1", now));
    }

    private void prepareDailyResponses(UserEntity elder, SessionEntity session, Instant answeredAt) {
        QuestionEntity orientation = questionRepository.findByQuestionCodeAndActiveTrue("orientation_year")
                .orElseThrow();
        QuestionEntity attention = questionRepository.findByQuestionCodeAndActiveTrue("attention_digit_span_4")
                .orElseThrow();
        addDailyResponse(elder, session, orientation, 2, answeredAt);
        addDailyResponse(elder, session, attention, 6, answeredAt.plusSeconds(20));
    }

    private DailyAnalysisCreateRequest createAndCaptureDailyRequest(UUID userId, UUID sessionId, int invocationCount) {
        assertThat(analysisService.createDailyAnalysis(userId, sessionId).status()).isEqualTo("pending");
        ArgumentCaptor<DailyAnalysisCreateRequest> captor = ArgumentCaptor.forClass(DailyAnalysisCreateRequest.class);
        verify(aiServerClient, times(invocationCount)).createDailyCognitiveAnalysis(anyString(), captor.capture());
        return captor.getAllValues().get(invocationCount - 1);
    }

    private void completeDailyRequest(
            UUID userId, DailyAnalysisCreateRequest request, String score, Instant completedAt
    ) throws Exception {
        BigDecimal estimatedScore = new BigDecimal(score);
        BigDecimal inputScore = request.inputSnapshot().modelScore();
        var features = features("0.10", "0.20", "0.30", "0.40");
        var outputSnapshot = featureSnapshot(fullQuestionResults(), estimatedScore, features);
        var questionResults = request.responses().stream()
                .map(response -> new AiServerContracts.QuestionAnalysisResult(
                        response.questionCode(), "administered", response.recordingId(), response.responseId(),
                        "speech_detected", "scored", "correct", 0, null, 500L, null))
                .toList();
        var result = new AiServerContracts.DailyAnalysisResult(
                "daily_partial_estimate", request.baselineAnalysisId(), request.baselineModelScore(),
                inputScore, estimatedScore, estimatedScore.subtract(request.baselineModelScore()),
                estimatedScore.subtract(inputScore), AiServerContracts.FUSION_MODEL_VERSION,
                new BigDecimal("0.38592870327757767"), new BigDecimal("0.8061380697921943"),
                AiServerContracts.THRESHOLD_VERSION, true, "monitoring_needed",
                request.responses().stream().map(AiServerContracts.AdministeredQuestionResponse::questionCode).toList(),
                features, outputSnapshot, questionResults);
        Instant synchronizedAt = Instant.now().plusSeconds(30);
        when(aiServerClient.getDailyCognitiveAnalysis(request.analysisId()))
                .thenReturn(new AiServerContracts.DailyAnalysisStatusResponse(
                        request.analysisId(), request.sessionId(), "completed", completedAt.minusSeconds(30),
                        synchronizedAt, false, null, List.of(), result));
        assertThat(analysisService.refreshDailyAnalysis(userId, request.sessionId()).status()).isEqualTo("completed");
        assertThat(dailyEstimateRepository.findBySessionId(request.sessionId()).orElseThrow().getStatus())
                .isEqualTo("completed");
    }

    private SessionEntity endedSession(
            UUID userId, String type, int questionCount, Instant startedAt, Instant endedAt
    ) {
        SessionEntity session = new SessionEntity(UUID.randomUUID(), userId, type, questionCount, "{}", false, startedAt);
        session.end(endedAt);
        return session;
    }

    private CistAiAnalysisEntity completedAnalysis(
            UUID sessionId,
            UUID baselineAnalysisId,
            String score,
            AiServerContracts.CognitiveFeatureSnapshot snapshot,
            Instant now
    ) throws Exception {
        UUID analysisId = UUID.randomUUID();
        CistAiAnalysisEntity entity = new CistAiAnalysisEntity(
                analysisId, sessionId, "completed", "test-create-" + analysisId, "0".repeat(64), "{}", now, now);
        entity.linkBaselineAnalysis(baselineAnalysisId);
        entity.updateStatus(
                "completed", false, null, null, objectMapper.writeValueAsString(snapshot),
                new BigDecimal(score), AiServerContracts.FUSION_MODEL_VERSION,
                new BigDecimal("0.38592870327757767"), new BigDecimal("0.8061380697921943"),
                AiServerContracts.THRESHOLD_VERSION, true, "monitoring_needed", now);
        entity.updateFeatureSnapshot(objectMapper.writeValueAsString(snapshot));
        return entity;
    }

    private AdministeredFixture addDailyResponse(
            UserEntity elder,
            SessionEntity session,
            QuestionEntity sourceQuestion,
            int questionOrder,
            Instant answeredAt
    ) {
        QuestionEntity dailyQuestion = questionRepository.save(new QuestionEntity(
                UUID.randomUUID(), sourceQuestion.getQuestionType(), "emotional_qa", sourceQuestion.getContent(),
                sourceQuestion.getHint(), questionOrder, true, session.getId(), "cist_bank", sourceQuestion.getId(),
                sourceQuestion.getVariantId(), sourceQuestion.getAdministrationMode(), answeredAt));
        SessionQuestionSlotEntity slot = new SessionQuestionSlotEntity(
                session.getId(), questionOrder, "cist_bank", sourceQuestion.getId());
        slot.assignQuestion(dailyQuestion.getId());
        slotRepository.save(slot);

        UUID recordingId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID transcriptId = UUID.randomUUID();
        recordingRepository.save(new RecordingEntity(
                recordingId, UUID.randomUUID(), elder.getId(), RecordingEntity.ANSWER,
                session.getId(), dailyQuestion.getId(), "daily/" + recordingId + ".wav", "answer.wav", "{}",
                "audio/wav", 2048, 3500, answeredAt, answeredAt));
        transcriptRepository.save(new TranscriptEntity(
                transcriptId, recordingId, "답변 전사문", new BigDecimal("3.500"), BigDecimal.ONE,
                "ko", "Google STT", "chirp_3", "completed", answeredAt, answeredAt, answeredAt));
        answerRepository.save(new AnswerEntity(
                answerId, session.getId(), dailyQuestion.getId(), UUID.randomUUID(), "답변 전사문",
                recordingId, transcriptId, 800, answeredAt, answeredAt));
        return new AdministeredFixture(sourceQuestion.getQuestionCode(), recordingId, answerId);
    }

    private AiServerContracts.FusionFeatures features(String ast, String kc, String wrong, String delay) {
        return new AiServerContracts.FusionFeatures(
                new BigDecimal(ast), new BigDecimal(kc), new BigDecimal(wrong), new BigDecimal(delay));
    }

    private record AdministeredFixture(String questionCode, UUID recordingId, UUID answerId) {
    }
}
