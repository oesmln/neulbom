package com.neulbom.backend.analysis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.analysis.integration.AcousticAnalysisClient;
import com.neulbom.backend.analysis.integration.CognitiveAnalysisClient;
import com.neulbom.backend.analysis.integration.SpeechToTextClient;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.recording.RecordingStorage;
import com.neulbom.backend.recording.TranscriptEntity;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisIntegrationTest {

    private static final UUID QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private RecordingStorage recordingStorage;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Autowired
    private UuidGenerator uuidGenerator;

    @MockitoBean
    private SpeechToTextClient speechToTextClient;

    @MockitoBean
    private AcousticAnalysisClient acousticAnalysisClient;

    @MockitoBean
    private CognitiveAnalysisClient cognitiveAnalysisClient;

    @BeforeEach
    void stubCistProviders() {
        when(speechToTextClient.isConfigured()).thenReturn(true);
        when(speechToTextClient.transcribe(any())).thenReturn(new SpeechToTextClient.TranscriptionResult(
                "2026년입니다.", BigDecimal.ONE, BigDecimal.ONE, "ko", "Google STT", "test-v1"));
        when(acousticAnalysisClient.isConfigured()).thenReturn(true);
        when(acousticAnalysisClient.analyze(any(), any(), any(Integer.class), any()))
                .thenReturn(new AcousticAnalysisClient.AcousticResult(
                        new BigDecimal("0.75"), JsonNodeFactory.instance.objectNode(),
                        new BigDecimal("3.2"), new BigDecimal("0.15"),
                        new BigDecimal("0.6"), new BigDecimal("0.8"), "AST", "test-v1"));
        when(cognitiveAnalysisClient.isConfigured()).thenReturn(true);
        var domainScores = JsonNodeFactory.instance.objectNode();
        domainScores.putObject("orientation").put("correct", 1).put("total", 1).put("score_rate", 0.8);
        when(cognitiveAnalysisClient.analyze(any(), any(), any(), any(), any()))
                .thenReturn(new CognitiveAnalysisClient.CognitiveResult(
                        new BigDecimal("0.8"), JsonNodeFactory.instance.objectNode(),
                        domainScores, JsonNodeFactory.instance.objectNode(),
                        "KcELECTRA", "test-v1"));
    }

    @Test
    void serverWorkerRunsSttAcousticCognitiveSummaryAndDailyAggregation() throws Exception {
        UserEntity elder = saveUser("analysis-owner");
        Instant now = Instant.now();
        SessionEntity session = sessionRepository.save(new SessionEntity(
                uuidGenerator.generate(), elder.getId(), "cist", 5, "{}", false, now));
        UUID recordingId = uuidGenerator.generate();
        String storageKey = recordingStorage.store(recordingId,
                new MockMultipartFile("audio_file", "analysis.wav", "audio/wav", new byte[]{1}));
        RecordingEntity recording = recordingRepository.save(new RecordingEntity(
                recordingId, uuidGenerator.generate(), elder.getId(), RecordingEntity.ANSWER,
                session.getId(), QUESTION_ID, storageKey, "analysis.wav", "{}", "audio/wav", 1,
                1_000,
                now.minusSeconds(1), now));

        String transcriptBody = mockMvc.perform(multipart("/api/v1/voice/transcribe")
                        .file(new MockMultipartFile("audio_file", "analysis.wav", "audio/wav", new byte[]{1}))
                        .with(serverJwt(elder))
                        .param("recording_id", recording.getId().toString())
                        .param("user_id", elder.getId().toString())
                        .param("session_id", session.getId().toString())
                        .param("question_id", QUESTION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recording_id").value(recording.getId().toString()))
                .andExpect(jsonPath("$.transcript").isNotEmpty())
                .andExpect(jsonPath("$.language").value("ko"))
                .andReturn().getResponse().getContentAsString();
        UUID transcriptId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(transcriptBody).get("transcript_id").asText());

        String acousticBody = mockMvc.perform(post("/api/v1/analysis/acoustic")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recording_id": "%s",
                                  "user_id": "%s",
                                  "session_id": "%s",
                                  "model_version": "ast-test-v1"
                                }
                                """.formatted(recording.getId(), elder.getId(), session.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoustic_reference_score").value(0.75))
                .andExpect(jsonPath("$.acoustic_flags").isMap())
                .andReturn().getResponse().getContentAsString();
        UUID acousticId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(acousticBody).get("acoustic_analysis_id").asText());

        String cognitiveBody = mockMvc.perform(post("/api/v1/analysis/cognitive")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transcript_id": "%s",
                                  "transcript": "오늘은 좋은 하루입니다.",
                                  "user_id": "%s",
                                  "session_id": "%s",
                                  "question_id": "%s",
                                  "question_type": "orientation",
                                  "acoustic_analysis_id": "%s",
                                  "fusion_mode": "weighted_average"
                                }
                                """.formatted(transcriptId, elder.getId(), session.getId(), QUESTION_ID, acousticId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis_id").isNotEmpty())
                .andExpect(jsonPath("$.screening_reference_score").isNumber())
                .andExpect(jsonPath("$.domain_scores.orientation.total").value(1))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(cognitiveBody).contains("risk_level");
        verify(cognitiveAnalysisClient).analyze(
                eq(QUESTION_ID),
                eq("오늘은 몇 년도인지 말씀해 주세요."),
                eq("2026년입니다."),
                eq("orientation"),
                eq("kcelectra-cist-v1"));

        mockMvc.perform(post("/api/v1/summary/session")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "session_id": "%s",
                                  "user_id": "%s",
                                  "qa_pairs": [
                                    {"question_id":"%s","question":"오늘은 몇 년도인가요?","answer":"2026년입니다.","question_type":"orientation"}
                                  ]
                                }
                                """.formatted(session.getId(), elder.getId(), QUESTION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary_id").isNotEmpty())
                .andExpect(jsonPath("$.qa_count").value(1));

        mockMvc.perform(get("/api/v1/summary/session/{sessionId}", session.getId())
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value(session.getId().toString()));

        LocalDate localDate = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        mockMvc.perform(post("/api/v1/summary/daily")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + elder.getId() + "\",\"local_date\":\"" + localDate + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.local_date").value(localDate.toString()))
                .andExpect(jsonPath("$.analyzed_session_count").value(1));

        mockMvc.perform(get("/api/v1/summary/daily/{userId}", elder.getId())
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily_summaries.length()").value(1));
    }

    @Test
    void appTokenCannotCallServerWorkerAnalysis() throws Exception {
        UserEntity elder = saveUser("analysis-client");

        mockMvc.perform(post("/api/v1/analysis/acoustic")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recording_id":"%s","user_id":"%s","session_id":"%s"}
                                """.formatted(UUID.randomUUID(), elder.getId(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void cistCognitiveAnalysisRequiresTheRecordingQuestionId() throws Exception {
        UserEntity elder = saveUser("analysis-question-id");
        Instant now = Instant.now();
        SessionEntity session = sessionRepository.save(new SessionEntity(
                uuidGenerator.generate(), elder.getId(), "cist", 5, "{}", false, now));
        RecordingEntity recording = recordingRepository.save(new RecordingEntity(
                uuidGenerator.generate(), uuidGenerator.generate(), elder.getId(), RecordingEntity.ANSWER,
                session.getId(), QUESTION_ID, "recordings/question.wav", "question.wav", "{}", "audio/wav", 1,
                1_000, now.minusSeconds(1), now));
        TranscriptEntity transcript = transcriptRepository.save(new TranscriptEntity(
                uuidGenerator.generate(), recording.getId(), "2026년입니다.", BigDecimal.ONE, BigDecimal.ONE,
                "ko-KR", "chirp_3", "v2", "completed", now, now, now));

        String requestWithoutQuestionId = """
                {
                  "transcript_id": "%s",
                  "user_id": "%s",
                  "session_id": "%s",
                  "question_type": "orientation"
                }
                """.formatted(transcript.getId(), elder.getId(), session.getId());
        mockMvc.perform(post("/api/v1/analysis/cognitive")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithoutQuestionId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("question_id를 확인하세요."));

        String requestWithWrongQuestionId = """
                {
                  "transcript_id": "%s",
                  "user_id": "%s",
                  "session_id": "%s",
                  "question_id": "%s",
                  "question_type": "orientation"
                }
                """.formatted(transcript.getId(), elder.getId(), session.getId(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/analysis/cognitive")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithWrongQuestionId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("녹음 문항과 분석 문항이 일치하지 않습니다."));

        verify(cognitiveAnalysisClient, never()).analyze(any(), any(), any(), any(), any());
    }

    private UserEntity saveUser(String prefix) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(
                id, prefix + "-" + id + "@example.com", null, "분석 사용자", "elder",
                LocalDate.of(1945, 1, 1), "80s_plus", "female", null, false, now, now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor serverJwt(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()))
                .authorities(new SimpleGrantedAuthority("SCOPE_server:write"));
    }
}
