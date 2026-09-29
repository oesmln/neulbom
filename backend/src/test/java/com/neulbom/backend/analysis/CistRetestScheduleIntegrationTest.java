package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CistRetestScheduleIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private SessionRepository sessions;
    @Autowired private CistAiAnalysisRepository analyses;

    @Test
    void scheduleUsesOnlyLatestCompletedFullCistAndResetsAfterRetest() throws Exception {
        UUID userId = saveUser();
        UUID otherUserId = saveUser();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate oldDate = today.minusMonths(4);
        SessionEntity oldCist = endedSession(userId, "cist", oldDate.atTime(12, 0).atZone(BUSINESS_ZONE).toInstant());
        saveAnalysis(oldCist, "completed");
        SessionEntity otherCist = endedSession(otherUserId, "cist",
                today.atTime(12, 0).atZone(BUSINESS_ZONE).toInstant());
        saveAnalysis(otherCist, "completed");
        SessionEntity daily = endedSession(userId, "emotional_qa",
                today.minusDays(2).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant());
        saveAnalysis(daily, "completed");
        SessionEntity pendingRetest = endedSession(userId, "baseline",
                today.minusDays(1).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant());
        CistAiAnalysisEntity pending = saveAnalysis(pendingRetest, "pending");

        mockMvc.perform(get("/api/v1/cist/retest-schedule")
                        .with(jwt().jwt(token -> token.subject(userId.toString()).claim("role", "elder"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_completed_session_id").value(oldCist.getId().toString()))
                .andExpect(jsonPath("$.last_completed_date").value(oldDate.toString()))
                .andExpect(jsonPath("$.next_due_date").value(oldDate.plusMonths(3).toString()))
                .andExpect(jsonPath("$.retest_due").value(true))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"));

        complete(pending);
        analyses.save(pending);
        mockMvc.perform(get("/api/v1/cist/retest-schedule")
                        .with(jwt().jwt(token -> token.subject(userId.toString()).claim("role", "elder"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_completed_session_id").value(pendingRetest.getId().toString()))
                .andExpect(jsonPath("$.last_completed_date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.next_due_date").value(today.minusDays(1).plusMonths(3).toString()))
                .andExpect(jsonPath("$.retest_due").value(false));
    }

    @Test
    void emptyScheduleDoesNotTreatPendingOrFailedAnalysesAsCompleted() throws Exception {
        UUID userId = saveUser();
        SessionEntity pending = endedSession(userId, "cist", Instant.now().minusSeconds(7200));
        SessionEntity failed = endedSession(userId, "onboarding", Instant.now().minusSeconds(3600));
        saveAnalysis(pending, "pending");
        saveAnalysis(failed, "failed");

        mockMvc.perform(get("/api/v1/cist/retest-schedule")
                        .with(jwt().jwt(token -> token.subject(userId.toString()).claim("role", "elder"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_completed_session_id").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.last_completed_date").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.next_due_date").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.retest_due").value(false));
        mockMvc.perform(get("/api/v1/cist/retest-schedule"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void calendarMonthsAndSeoulMidnightDetermineDueDate() {
        UUID userId = saveUser();
        SessionEntity session = endedSession(userId, "cist",
                LocalDate.of(2026, 1, 31).atTime(23, 30).atZone(BUSINESS_ZONE).toInstant());
        saveAnalysis(session, "completed");

        CistRetestScheduleService beforeDue = new CistRetestScheduleService(sessions, analyses,
                Clock.fixed(Instant.parse("2026-04-29T14:59:00Z"), ZoneOffset.UTC));
        CistRetestScheduleService onDue = new CistRetestScheduleService(sessions, analyses,
                Clock.fixed(Instant.parse("2026-04-29T15:00:00Z"), ZoneOffset.UTC));
        assertThat(beforeDue.getSchedule(userId).nextDueDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(beforeDue.getSchedule(userId).retestDue()).isFalse();
        assertThat(onDue.getSchedule(userId).retestDue()).isTrue();
    }

    private UUID saveUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        users.save(new UserEntity(id, "retest-" + id + "@example.com", null, "재검사 일정",
                "elder", null, null, null, null, true, now, now));
        return id;
    }

    private SessionEntity endedSession(UUID userId, String type, Instant endedAt) {
        SessionEntity session = new SessionEntity(UUID.randomUUID(), userId, type, 17, "{}", false,
                endedAt.minusSeconds(600));
        session.end(endedAt);
        return sessions.save(session);
    }

    private CistAiAnalysisEntity saveAnalysis(SessionEntity session, String status) {
        UUID id = UUID.randomUUID();
        CistAiAnalysisEntity analysis = new CistAiAnalysisEntity(id, session.getId(), "pending",
                "retest-" + id, "0".repeat(64), "{}", session.getEndedAt(), session.getEndedAt());
        if ("completed".equals(status)) {
            complete(analysis);
        } else if ("failed".equals(status)) {
            analysis.updateStatus("failed", false, "MODEL_UNAVAILABLE", null, null, null, null,
                    null, null, null, null, null, session.getEndedAt());
        }
        return analyses.save(analysis);
    }

    private void complete(CistAiAnalysisEntity analysis) {
        analysis.updateStatus("completed", false, null, null, "{}", new BigDecimal("0.42"),
                "test-model", new BigDecimal("0.38"), new BigDecimal("0.80"), "test-threshold",
                true, "monitoring_needed", Instant.now());
    }
}
