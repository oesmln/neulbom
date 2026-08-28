package com.neulbom.backend.report;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.analysis.CognitiveAnalysisEntity;
import com.neulbom.backend.analysis.CognitiveAnalysisRepository;
import com.neulbom.backend.analysis.ScreeningResultEntity;
import com.neulbom.backend.analysis.ScreeningResultRepository;
import com.neulbom.backend.analysis.SessionSummaryEntity;
import com.neulbom.backend.analysis.SessionSummaryRepository;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.guardian.GuardianLinkEntity;
import com.neulbom.backend.guardian.GuardianLinkRepository;
import com.neulbom.backend.guardian.GuardianLinkScopeEntity;
import com.neulbom.backend.guardian.GuardianLinkScopeRepository;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.recording.TranscriptEntity;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportIntegrationTest {

    private static final UUID QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private RecordingRepository recordingRepository;
    @Autowired private TranscriptRepository transcriptRepository;
    @Autowired private CognitiveAnalysisRepository cognitiveAnalysisRepository;
    @Autowired private ScreeningResultRepository screeningResultRepository;
    @Autowired private SessionSummaryRepository sessionSummaryRepository;
    @Autowired private GuardianLinkRepository guardianLinkRepository;
    @Autowired private GuardianLinkScopeRepository guardianLinkScopeRepository;
    @Autowired private UuidGenerator uuidGenerator;

    @Test
    void resultHistoryDashboardReportAndExportRespectAudienceAndScopes() throws Exception {
        UserEntity guardian = saveUser("report-guardian", "guardian");
        UserEntity elder = saveUser("report-elder", "elder");
        Instant analyzedAt = Instant.now().minusSeconds(60);
        SessionEntity session = sessionRepository.save(new SessionEntity(uuidGenerator.generate(), elder.getId(), "cist", 5, "{}", false, analyzedAt));
        SessionEntity completedEmotional = new SessionEntity(uuidGenerator.generate(), elder.getId(), "emotional_qa", 5, "{}", false, analyzedAt);
        completedEmotional.end(analyzedAt.plusSeconds(30));
        sessionRepository.save(completedEmotional);
        sessionRepository.save(new SessionEntity(uuidGenerator.generate(), elder.getId(), "emotional_qa", 5, "{}", false, analyzedAt));
        RecordingEntity recording = recordingRepository.save(new RecordingEntity(uuidGenerator.generate(), uuidGenerator.generate(), elder.getId(),
                RecordingEntity.ANSWER, session.getId(), QUESTION_ID, "recordings/report.wav", "report.wav", "{}", "audio/wav", 4,
                1_000, analyzedAt, analyzedAt));
        TranscriptEntity transcript = transcriptRepository.save(new TranscriptEntity(uuidGenerator.generate(), recording.getId(), "오늘은 좋은 하루입니다.",
                BigDecimal.ONE, new BigDecimal("0.8"), "ko", "test", "v1", "completed", analyzedAt, analyzedAt, analyzedAt));
        cognitiveAnalysisRepository.save(new CognitiveAnalysisEntity(uuidGenerator.generate(), transcript.getId(), null, elder.getId(), session.getId(),
                QUESTION_ID, "orientation", "none", "KcELECTRA", "v1", new BigDecimal("0.8"), new BigDecimal("0.8"),
                "normal", "normal", "{}", "{\"orientation\":{\"correct\":1,\"total\":1,\"score_rate\":0.8}}", "{}", analyzedAt, analyzedAt));
        screeningResultRepository.save(new ScreeningResultEntity(uuidGenerator.generate(), session.getId(), elder.getId(), "completed",
                new BigDecimal("0.8"), new BigDecimal("24.00"), new BigDecimal("30.00"), new BigDecimal("0.8"), "normal",
                "오늘 대화 결과가 좋아요", null, "{}", analyzedAt, analyzedAt, analyzedAt));
        sessionSummaryRepository.save(new SessionSummaryEntity(uuidGenerator.generate(), session.getId(), elder.getId(), "오늘 대화 요약",
                new BigDecimal("80"), "[]", 1, "completed", analyzedAt, analyzedAt));
        GuardianLinkEntity link = guardianLinkRepository.save(new GuardianLinkEntity(uuidGenerator.generate(), guardian.getId(), elder.getId(),
                "자녀", GuardianLinkEntity.ACTIVE, false, analyzedAt, analyzedAt));
        guardianLinkScopeRepository.save(new GuardianLinkScopeEntity(link.getId(), "screening"));
        guardianLinkScopeRepository.save(new GuardianLinkScopeEntity(link.getId(), "summary"));

        mockMvc.perform(get("/api/v1/screenings/{sessionId}/result", session.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audience").value("elder"))
                .andExpect(jsonPath("$.screening_reference_score").doesNotExist());
        mockMvc.perform(get("/api/v1/screenings/{sessionId}/result", session.getId()).with(jwtFor(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audience").value("guardian"))
                .andExpect(jsonPath("$.screening_reference_score").value(0.8))
                .andExpect(jsonPath("$.domain_scores").isMap());
        mockMvc.perform(get("/api/v1/analysis/cognitive/{userId}/history", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.records[0].display_score").doesNotExist());
        mockMvc.perform(get("/api/v1/analysis/cognitive/{userId}/benchmark", elder.getId()).with(jwtFor(guardian))
                        .param("province_code", "26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regional_series[0].suppressed").value(true));
        mockMvc.perform(get("/api/v1/dashboard/{userId}", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cognitive_activity.status").value("stable"))
                .andExpect(jsonPath("$.monthly_activity.emotional_qa_completed_count").value(1));
        mockMvc.perform(get("/api/v1/guardian/{guardianId}/report", guardian.getId()).with(jwtFor(guardian))
                        .param("elder_id", elder.getId().toString()).param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elder_id").value(elder.getId().toString()))
                .andExpect(jsonPath("$.latest_display_score").value(24.0));
        mockMvc.perform(get("/api/v1/guardian/{guardianId}/report/export", guardian.getId()).with(jwtFor(guardian))
                        .param("elder_id", elder.getId().toString()).param("from_date", LocalDate.now().minusDays(1).toString())
                        .param("to_date", LocalDate.now().toString()).param("format", "csv"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("processing"));
    }

    private UserEntity saveUser(String prefix, String role) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(id, prefix + "-" + id + "@example.com", null, prefix, role,
                LocalDate.of(1945, 1, 1), "80s_plus", "female", null, false, now, now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()));
    }
}
