package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DailyCognitiveTrendIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private SessionRepository sessions;
    @Autowired private CistAiAnalysisRepository analyses;
    @Autowired private CognitiveFeatureSnapshotService baselines;
    @Autowired private DailyCognitiveEstimateService estimates;

    @Test
    void dailySnapshotsFollowCompletedParentUntilNewBaseline() throws Exception {
        UUID userId = saveUser();
        CognitiveFeatureSnapshotEntity firstBaseline = saveBaseline(userId, "cist", "0.42", "{\"step\":0}");
        assertThat(baselines.findLatestBaselineSnapshot(userId).orElseThrow().getSnapshotId())
                .isEqualTo(firstBaseline.getSnapshotId());
        assertThat(baselines.saveBaselineSnapshot(userId, firstBaseline.getSourceSessionId(),
                firstBaseline.getSourceAnalysisId(), "cist-v1", "test-model", "test-threshold",
                new BigDecimal("0.42"), "{\"step\":0}").getSnapshotId())
                .isEqualTo(firstBaseline.getSnapshotId());
        assertThatThrownBy(() -> baselines.saveBaselineSnapshot(userId, firstBaseline.getSourceSessionId(),
                firstBaseline.getSourceAnalysisId(), "cist-v1", "test-model", "test-threshold",
                new BigDecimal("0.42"), "{\"step\":99}"))
                .hasMessageContaining("덮어쓸 수 없습니다");

        UUID firstSessionId = endedDailySession(userId);
        DailyCognitiveEstimateEntity first = estimates.createDailyEstimate(userId, firstSessionId);
        assertThat(first.getBaselineSnapshotId()).isEqualTo(firstBaseline.getSnapshotId());
        assertThat(first.getParentEstimateId()).isNull();
        assertThat(new ObjectMapper().readTree(estimates.findInputSnapshot(first.getEstimateId()).featureSnapshot()))
                .isEqualTo(new ObjectMapper().readTree("{\"step\":0}"));
        assertThat(estimates.createDailyEstimate(userId, firstSessionId).getEstimateId()).isEqualTo(first.getEstimateId());
        assertThatThrownBy(() -> estimates.createDailyEstimate(userId, endedDailySession(userId)))
                .hasMessageContaining("같은 기준 계보의 분석이 완료된 뒤");

        DailyEstimateCompletion firstResult = new DailyEstimateCompletion(
                new BigDecimal("0.450000000019"), new BigDecimal("0.030000000019"),
                "test-model", "test-threshold", "stable", "{\"source\":\"ai\"}",
                Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS), "{\"step\":1}");
        estimates.markProcessing(first.getEstimateId());
        estimates.markFailed(first.getEstimateId());
        assertThat(estimates.createDailyEstimate(userId, firstSessionId).getEstimateId())
                .isEqualTo(first.getEstimateId());
        estimates.completeDailyEstimate(first.getEstimateId(), firstResult);
        assertThat(estimates.createDailyEstimate(userId, firstSessionId).getEstimatedModelScore())
                .isEqualByComparingTo("0.45");
        assertThat(estimates.completeDailyEstimate(first.getEstimateId(), firstResult).getEstimateId())
                .isEqualTo(first.getEstimateId());
        assertThatThrownBy(() -> estimates.completeDailyEstimate(first.getEstimateId(), completion("0.50", "{\"step\":99}")))
                .hasMessageContaining("완료된 결과와 특징 스냅샷은 덮어쓸 수 없습니다");

        DailyCognitiveEstimateEntity second = estimates.createDailyEstimate(userId, endedDailySession(userId));
        assertThat(second.getParentEstimateId()).isEqualTo(first.getEstimateId());
        assertThat(new ObjectMapper().readTree(estimates.findInputSnapshot(second.getEstimateId()).featureSnapshot()))
                .isEqualTo(new ObjectMapper().readTree("{\"step\":1}"));
        estimates.completeDailyEstimate(second.getEstimateId(), completion("0.47", "{\"step\":2}"));
        assertThat(estimates.findLatestCurrentSnapshot(userId, firstBaseline.getSnapshotId()).parentEstimateId())
                .isEqualTo(second.getEstimateId());

        CognitiveFeatureSnapshotEntity nextBaseline = saveBaseline(userId, "baseline", "0.35", "{\"step\":0,\"new\":true}");
        assertThatThrownBy(() -> estimates.createDailyEstimate(
                userId, firstSessionId, nextBaseline.getSnapshotId()))
                .hasMessageContaining("기준 스냅샷은 변경할 수 없습니다");
        DailyCognitiveEstimateEntity afterRetest = estimates.createDailyEstimate(userId, endedDailySession(userId));
        assertThat(afterRetest.getBaselineSnapshotId()).isEqualTo(nextBaseline.getSnapshotId());
        assertThat(afterRetest.getParentEstimateId()).isNull();
        assertThat(new ObjectMapper().readTree(estimates.findInputSnapshot(afterRetest.getEstimateId()).featureSnapshot()))
                .isEqualTo(new ObjectMapper().readTree("{\"step\":0,\"new\":true}"));
        assertThat(estimates.findLatestCurrentSnapshot(userId, firstBaseline.getSnapshotId()).parentEstimateId())
                .isEqualTo(second.getEstimateId());
    }

    @Test
    void laterDailySessionDoesNotBecomeParentOfEarlierSession() {
        UUID userId = saveUser();
        CognitiveFeatureSnapshotEntity baseline = saveBaseline(userId, "cist", "0.42", "{\"step\":0}");
        Instant firstStart = Instant.now().plusSeconds(60);
        UUID laterSessionId = endedDailySession(userId, firstStart.plusSeconds(3600));
        DailyCognitiveEstimateEntity later = estimates.createDailyEstimate(
                userId, laterSessionId, baseline.getSnapshotId());
        estimates.completeDailyEstimate(later.getEstimateId(), completion("0.45", "{\"step\":1}"));

        UUID earlierSessionId = endedDailySession(userId, firstStart);
        DailyCognitiveEstimateEntity earlier = estimates.createDailyEstimate(
                userId, earlierSessionId, baseline.getSnapshotId());
        assertThat(earlier.getBaselineSnapshotId()).isEqualTo(baseline.getSnapshotId());
        assertThat(earlier.getParentEstimateId()).isNull();
    }

    private UUID saveUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        users.save(new UserEntity(id, "trend-" + id + "@example.com", null, "trend", "elder",
                null, null, null, null, false, now, now));
        return id;
    }

    private CognitiveFeatureSnapshotEntity saveBaseline(UUID userId, String type, String score, String snapshot) {
        Instant now = Instant.now();
        SessionEntity session = sessions.save(new SessionEntity(UUID.randomUUID(), userId, type, 17, "{}", false, now));
        session.end(now);
        sessions.save(session);
        CistAiAnalysisEntity analysis = new CistAiAnalysisEntity(UUID.randomUUID(), session.getId(), "pending",
                "trend-" + UUID.randomUUID(), "a".repeat(64), "{}", now, now);
        analysis.updateStatus("completed", false, null, null, "{}", new BigDecimal(score),
                "test-model", new BigDecimal("0.38"), new BigDecimal("0.80"), "test-threshold",
                false, "stable", now);
        analyses.save(analysis);
        return baselines.saveBaselineSnapshot(userId, session.getId(), analysis.getAnalysisId(),
                "cist-v1", "test-model", "test-threshold", new BigDecimal(score), snapshot);
    }

    private UUID endedDailySession(UUID userId) {
        Instant now = Instant.now();
        SessionEntity session = new SessionEntity(UUID.randomUUID(), userId, "emotional_qa", 7, "{}", false, now);
        session.end(now);
        sessions.save(session);
        return session.getId();
    }

    private UUID endedDailySession(UUID userId, Instant startedAt) {
        SessionEntity session = new SessionEntity(
                UUID.randomUUID(), userId, "emotional_qa", 7, "{}", false, startedAt);
        session.end(startedAt.plusSeconds(30));
        sessions.save(session);
        return session.getId();
    }

    private DailyEstimateCompletion completion(String score, String snapshot) {
        return new DailyEstimateCompletion(new BigDecimal(score), new BigDecimal("0.03"),
                "test-model", "test-threshold", "stable", "{\"source\":\"ai\"}",
                Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS), snapshot);
    }
}
