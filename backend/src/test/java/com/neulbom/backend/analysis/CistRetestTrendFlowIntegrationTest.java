package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.guardian.GuardianLinkEntity;
import com.neulbom.backend.guardian.GuardianLinkRepository;
import com.neulbom.backend.guardian.GuardianLinkScopeEntity;
import com.neulbom.backend.guardian.GuardianLinkScopeRepository;
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
class CistRetestTrendFlowIntegrationTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private SessionRepository sessions;
    @Autowired private CistAiAnalysisRepository analyses;
    @Autowired private CognitiveFeatureSnapshotService snapshots;
    @Autowired private DailyCognitiveEstimateService estimates;
    @Autowired private GuardianLinkRepository links;
    @Autowired private GuardianLinkScopeRepository scopes;
    @Autowired private ObjectMapper json;

    @Test
    void retestStartsNewLineageWhileGuardianHistoryRetainsBothKindsOfPoints() throws Exception {
        UserEntity elder = saveUser("elder");
        UserEntity guardian = saveUser("guardian");
        Instant now = Instant.now();
        GuardianLinkEntity link = links.save(new GuardianLinkEntity(UUID.randomUUID(), guardian.getId(),
                elder.getId(), "자녀", GuardianLinkEntity.ACTIVE, false, now, now));
        scopes.save(new GuardianLinkScopeEntity(link.getId(), "screening"));
        scopes.save(new GuardianLinkScopeEntity(link.getId(), "summary"));

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Instant firstEnd = today.minusMonths(4).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant();
        SessionEntity firstCist = endedSession(elder.getId(), "baseline", firstEnd.minusSeconds(600), firstEnd);
        CistAiAnalysisEntity firstAnalysis = completedAnalysis(firstCist, "0.42", firstEnd.plusSeconds(60));
        CognitiveFeatureSnapshotEntity firstBaseline = saveBaseline(elder.getId(), firstCist, firstAnalysis,
                "0.42", "{\"step\":0}");

        mockMvc.perform(get("/api/v1/cist/retest-schedule").with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_completed_session_id").value(firstCist.getId().toString()))
                .andExpect(jsonPath("$.next_due_date").value(today.minusMonths(4).plusMonths(3).toString()))
                .andExpect(jsonPath("$.retest_due").value(true));

        SessionEntity firstDaily = endedSession(elder.getId(), "emotional_qa",
                firstEnd.plusSeconds(86400), firstEnd.plusSeconds(86460));
        DailyCognitiveEstimateEntity firstEstimate = estimates.createDailyEstimate(elder.getId(), firstDaily.getId());
        assertThat(firstEstimate.getBaselineSnapshotId()).isEqualTo(firstBaseline.getSnapshotId());
        assertThat(firstEstimate.getParentEstimateId()).isNull();
        assertThat(json.readTree(estimates.findInputSnapshot(firstEstimate.getEstimateId()).featureSnapshot()))
                .isEqualTo(json.readTree("{\"step\":0}"));
        estimates.completeDailyEstimate(firstEstimate.getEstimateId(), completion("0.45", "0.03",
                "{\"step\":1}", now.plusSeconds(10)));

        SessionEntity secondDaily = endedSession(elder.getId(), "emotional_qa",
                firstEnd.plusSeconds(172800), firstEnd.plusSeconds(172860));
        DailyCognitiveEstimateEntity secondEstimate = estimates.createDailyEstimate(elder.getId(), secondDaily.getId());
        assertThat(secondEstimate.getParentEstimateId()).isEqualTo(firstEstimate.getEstimateId());
        assertThat(json.readTree(estimates.findInputSnapshot(secondEstimate.getEstimateId()).featureSnapshot()))
                .isEqualTo(json.readTree("{\"step\":1}"));
        estimates.completeDailyEstimate(secondEstimate.getEstimateId(), completion("0.47", "0.05",
                "{\"step\":2}", now.plusSeconds(20)));

        // 일상 부분 갱신은 재검사 예정일을 바꾸지 않는다.
        mockMvc.perform(get("/api/v1/cist/retest-schedule").with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_completed_session_id").value(firstCist.getId().toString()))
                .andExpect(jsonPath("$.retest_due").value(true));

        Instant retestEnd = today.minusDays(1).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant();
        SessionEntity retest = endedSession(elder.getId(), "cist", retestEnd.minusSeconds(600), retestEnd);
        CistAiAnalysisEntity retestAnalysis = completedAnalysis(retest, "0.35", now.plusSeconds(30));
        CognitiveFeatureSnapshotEntity newBaseline = saveBaseline(elder.getId(), retest, retestAnalysis,
                "0.35", "{\"step\":0,\"new\":true}");
        assertThat(snapshots.findLatestBaselineSnapshot(elder.getId()).orElseThrow().getSnapshotId())
                .isEqualTo(newBaseline.getSnapshotId());
        mockMvc.perform(get("/api/v1/cist/retest-schedule").with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_completed_session_id").value(retest.getId().toString()))
                .andExpect(jsonPath("$.next_due_date").value(today.minusDays(1).plusMonths(3).toString()))
                .andExpect(jsonPath("$.retest_due").value(false));

        SessionEntity afterRetest = endedSession(elder.getId(), "emotional_qa",
                retestEnd.plusSeconds(3600), retestEnd.plusSeconds(3660));
        DailyCognitiveEstimateEntity newEstimate = estimates.createDailyEstimate(elder.getId(), afterRetest.getId());
        assertThat(newEstimate.getBaselineSnapshotId()).isEqualTo(newBaseline.getSnapshotId());
        assertThat(newEstimate.getParentEstimateId()).isNull();
        assertThat(json.readTree(estimates.findInputSnapshot(newEstimate.getEstimateId()).featureSnapshot()))
                .isEqualTo(json.readTree("{\"step\":0,\"new\":true}"));
        estimates.completeDailyEstimate(newEstimate.getEstimateId(), completion("0.36", "0.01",
                "{\"step\":1,\"new\":true}", now.plusSeconds(40)));
        assertThat(estimates.findLatestCurrentSnapshot(elder.getId(), firstBaseline.getSnapshotId())
                .parentEstimateId()).isEqualTo(secondEstimate.getEstimateId());

        mockMvc.perform(get("/api/v1/guardian/{guardianId}/report", guardian.getId())
                        .with(jwtFor(guardian)).param("elder_id", elder.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai_risk_trend_points.length()").value(5))
                .andExpect(jsonPath("$.ai_risk_trend_points[0].point_type").value("full_cist"))
                .andExpect(jsonPath("$.ai_risk_trend_points[1].point_type").value("daily_partial_estimate"))
                .andExpect(jsonPath("$.ai_risk_trend_points[1].baseline_session_id")
                        .value(firstCist.getId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points[2].baseline_snapshot_id")
                        .value(firstBaseline.getSnapshotId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points[3].point_type").value("full_cist"))
                .andExpect(jsonPath("$.ai_risk_trend_points[3].baseline_snapshot_id")
                        .value(newBaseline.getSnapshotId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points[4].baseline_snapshot_id")
                        .value(newBaseline.getSnapshotId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points[4].baseline_session_id")
                        .value(retest.getId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points[4].is_estimated").value(true));
        mockMvc.perform(get("/api/v1/guardian/{guardianId}/report", guardian.getId())
                        .with(jwtFor(guardian)).param("elder_id", elder.getId().toString())
                        .param("from_date", today.minusMonths(1).toString())
                        .param("to_date", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prior_cist_baseline.session_id")
                        .value(firstCist.getId().toString()))
                .andExpect(jsonPath("$.prior_cist_baseline.baseline_snapshot_id")
                        .value(firstBaseline.getSnapshotId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points.length()").value(4));
        mockMvc.perform(get("/api/v1/analysis/cognitive/{userId}/history", elder.getId())
                        .with(jwtFor(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai_risk_trend_points.length()").value(5));
        mockMvc.perform(get("/api/v1/analysis/cognitive/{userId}/history", elder.getId())
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai_risk_trend_points.length()").value(0));
    }

    @Test
    void guardianReportAndHistoryKeepSeoulDateBoundariesAndPriorCistSeparate() throws Exception {
        UserEntity elder = saveUser("elder");
        UserEntity guardian = saveUser("guardian");
        Instant now = Instant.now();
        GuardianLinkEntity link = links.save(new GuardianLinkEntity(UUID.randomUUID(), guardian.getId(),
                elder.getId(), "자녀", GuardianLinkEntity.ACTIVE, false, now, now));
        scopes.save(new GuardianLinkScopeEntity(link.getId(), "screening"));
        scopes.save(new GuardianLinkScopeEntity(link.getId(), "summary"));

        LocalDate day = LocalDate.now(BUSINESS_ZONE).plusDays(2);
        Instant start = day.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        SessionEntity older = endedSession(elder.getId(), "cist",
                start.minusSeconds(172800), start.minusSeconds(172200));
        completedAnalysis(older, "0.20", start.minusSeconds(172140));
        SessionEntity prior = endedSession(elder.getId(), "cist",
                start.minusSeconds(660), start.minusSeconds(60));
        CistAiAnalysisEntity priorAnalysis = completedAnalysis(prior, "0.30", start.minusNanos(1000));
        CognitiveFeatureSnapshotEntity priorSnapshot = saveBaseline(elder.getId(), prior, priorAnalysis,
                "0.30", "{\"boundary\":true}");
        SessionEntity atStart = endedSession(elder.getId(), "cist",
                start.minusSeconds(600), start.minusSeconds(1));
        completedAnalysis(atStart, "0.40", start);
        SessionEntity atEnd = endedSession(elder.getId(), "cist",
                end.minusSeconds(660), end.minusSeconds(60));
        completedAnalysis(atEnd, "0.50", end.minusNanos(1000));
        SessionEntity after = endedSession(elder.getId(), "cist",
                end.minusSeconds(600), end.minusSeconds(1));
        completedAnalysis(after, "0.60", end);

        mockMvc.perform(get("/api/v1/guardian/{guardianId}/report", guardian.getId())
                        .with(jwtFor(guardian)).param("elder_id", elder.getId().toString())
                        .param("from_date", day.toString()).param("to_date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prior_cist_baseline.session_id").value(prior.getId().toString()))
                .andExpect(jsonPath("$.prior_cist_baseline.risk_score").value(0.30))
                .andExpect(jsonPath("$.prior_cist_baseline.baseline_snapshot_id")
                        .value(priorSnapshot.getSnapshotId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points.length()").value(2))
                .andExpect(jsonPath("$.ai_risk_trend_points[0].session_id")
                        .value(atStart.getId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points[1].session_id")
                        .value(atEnd.getId().toString()));
        mockMvc.perform(get("/api/v1/analysis/cognitive/{userId}/history", elder.getId())
                        .with(jwtFor(guardian))
                        .param("from_date", day.toString()).param("to_date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prior_cist_baseline.session_id").value(prior.getId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points.length()").value(2));
        LocalDate noCistDay = day.plusDays(2);
        mockMvc.perform(get("/api/v1/guardian/{guardianId}/report", guardian.getId())
                        .with(jwtFor(guardian)).param("elder_id", elder.getId().toString())
                        .param("from_date", noCistDay.toString())
                        .param("to_date", noCistDay.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prior_cist_baseline.session_id").value(after.getId().toString()))
                .andExpect(jsonPath("$.ai_risk_trend_points.length()").value(0));
        mockMvc.perform(get("/api/v1/guardian/{guardianId}/report", guardian.getId())
                        .with(jwtFor(guardian)).param("elder_id", elder.getId().toString())
                        .param("from_date", day.plusDays(1).toString())
                        .param("to_date", day.toString()))
                .andExpect(status().isBadRequest());
    }

    private UserEntity saveUser(String role) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return users.save(new UserEntity(id, "flow-" + id + "@example.com", null, "flow", role,
                null, null, null, null, true, now, now));
    }

    private SessionEntity endedSession(UUID userId, String type, Instant startedAt, Instant endedAt) {
        SessionEntity session = new SessionEntity(UUID.randomUUID(), userId, type,
                "emotional_qa".equals(type) ? 7 : 17, "{}", false, startedAt);
        if (!"emotional_qa".equals(type)) {
            for (int index = 0; index < session.getTotalQuestions(); index++) session.recordAnswer();
        }
        session.end(endedAt);
        return sessions.save(session);
    }

    private CistAiAnalysisEntity completedAnalysis(SessionEntity session, String score, Instant analyzedAt) {
        UUID analysisId = UUID.randomUUID();
        CistAiAnalysisEntity analysis = new CistAiAnalysisEntity(analysisId, session.getId(), "pending",
                "flow-" + analysisId, "a".repeat(64), "{}", session.getEndedAt(), session.getEndedAt());
        BigDecimal modelScore = new BigDecimal(score);
        boolean flagged = modelScore.compareTo(new BigDecimal("0.38")) >= 0;
        analysis.updateStatus("completed", false, null, null, "{}", modelScore,
                "test-model", new BigDecimal("0.38"), new BigDecimal("0.80"), "test-threshold",
                flagged, flagged ? "monitoring_needed" : "stable", analyzedAt);
        return analyses.save(analysis);
    }

    private CognitiveFeatureSnapshotEntity saveBaseline(UUID userId, SessionEntity session,
            CistAiAnalysisEntity analysis, String score, String featureSnapshot) {
        return snapshots.saveBaselineSnapshot(userId, session.getId(), analysis.getAnalysisId(),
                "cist-v1", "test-model", "test-threshold", new BigDecimal(score), featureSnapshot);
    }

    private DailyEstimateCompletion completion(String score, String delta, String output, Instant analyzedAt) {
        return new DailyEstimateCompletion(new BigDecimal(score), new BigDecimal(delta),
                "test-model", "test-threshold", "stable", "{\"source\":\"test\"}", analyzedAt, output);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()).claim("role", user.getRole()));
    }
}
