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
import java.util.UUID;

import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
    private UuidGenerator uuidGenerator;

    @Test
    void serverWorkerRunsSttAcousticCognitiveSummaryAndDailyAggregation() throws Exception {
        UserEntity elder = saveUser("analysis-owner");
        Instant now = Instant.now();
        SessionEntity session = sessionRepository.save(new SessionEntity(
                uuidGenerator.generate(), elder.getId(), "cist", 5, "{}", false, now));
        RecordingEntity recording = recordingRepository.save(new RecordingEntity(
                uuidGenerator.generate(), uuidGenerator.generate(), elder.getId(), RecordingEntity.ANSWER,
                session.getId(), QUESTION_ID, "recordings/analysis.wav", "analysis.wav", "{}", "audio/wav", 4,
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
