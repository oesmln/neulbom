package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CistAiAnalysisSnapshotPersistenceTest {

    @Autowired
    private CistAiAnalysisRepository analysisRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void savesAndReloadsCompletedFeatureSnapshot() throws Exception {
        UUID analysisId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-29T10:00:00Z");
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                "snapshot-" + UUID.randomUUID() + "@example.com",
                null,
                "스냅샷 저장 테스트",
                "elder",
                LocalDate.of(1945, 1, 1),
                "80s_plus",
                "female",
                null,
                true,
                now,
                now));
        UUID sessionId = UUID.randomUUID();
        sessionRepository.saveAndFlush(new SessionEntity(
                sessionId, user.getId(), "cist", 17, "{}", false, now));
        String featureSnapshot = """
                {"schema_version":"cognitive-feature-snapshot-v1", "model_score":0.61}
                """.trim();
        CistAiAnalysisEntity analysis = new CistAiAnalysisEntity(
                analysisId,
                sessionId,
                "pending",
                "snapshot-test-create-key",
                "request-hash",
                "{}",
                now,
                now);
        analysis.updateStatus(
                "completed",
                false,
                null,
                null,
                "{}",
                new BigDecimal("0.61"),
                "model-v2",
                new BigDecimal("0.38592870327757767"),
                new BigDecimal("0.8061380697921943"),
                "fusion-threshold-v2",
                true,
                "monitoring_needed",
                now.plusSeconds(1));
        analysis.updateFeatureSnapshot(featureSnapshot);

        analysisRepository.saveAndFlush(analysis);
        entityManager.clear();

        CistAiAnalysisEntity reloaded = analysisRepository.findById(analysisId).orElseThrow();
        assertThat(objectMapper.readTree(reloaded.getFeatureSnapshot()))
                .isEqualTo(objectMapper.readTree(featureSnapshot));
    }

    @Test
    void selectsOnlyInFlightDailyAnalysesForStatusSynchronization() {
        Instant now = Instant.parse("2026-09-29T10:00:00Z");
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(), "daily-sync-" + UUID.randomUUID() + "@example.com", null,
                "일상 상태 조회 테스트", "elder", LocalDate.of(1945, 1, 1), "80s_plus", "female", null,
                true, now, now));
        SessionEntity baselineSession = new SessionEntity(
                UUID.randomUUID(), user.getId(), "cist", 17, "{}", false, now);
        SessionEntity fullPendingSession = new SessionEntity(
                UUID.randomUUID(), user.getId(), "cist", 17, "{}", false, now.plusSeconds(1));
        SessionEntity firstDailySession = new SessionEntity(
                UUID.randomUUID(), user.getId(), "emotional_qa", 7, "{}", false, now.plusSeconds(2));
        SessionEntity secondDailySession = new SessionEntity(
                UUID.randomUUID(), user.getId(), "emotional_qa", 7, "{}", false, now.plusSeconds(3));
        SessionEntity completedDailySession = new SessionEntity(
                UUID.randomUUID(), user.getId(), "emotional_qa", 7, "{}", false, now.plusSeconds(4));
        sessionRepository.saveAllAndFlush(List.of(
                baselineSession, fullPendingSession, firstDailySession, secondDailySession, completedDailySession));

        CistAiAnalysisEntity baseline = analysis(baselineSession.getId(), "completed", null, now);
        CistAiAnalysisEntity fullPending = analysis(fullPendingSession.getId(), "pending", null, now);
        CistAiAnalysisEntity firstDaily = analysis(
                firstDailySession.getId(), "pending", baseline.getAnalysisId(), now.plusSeconds(2));
        CistAiAnalysisEntity secondDaily = analysis(
                secondDailySession.getId(), "processing", baseline.getAnalysisId(), now.plusSeconds(3));
        CistAiAnalysisEntity completedDaily = analysis(
                completedDailySession.getId(), "completed", baseline.getAnalysisId(), now.plusSeconds(4));
        analysisRepository.saveAndFlush(baseline);
        analysisRepository.saveAllAndFlush(List.of(fullPending, firstDaily, secondDaily, completedDaily));
        entityManager.clear();

        assertThat(analysisRepository
                .findTop100ByBaselineAnalysisIdIsNotNullAndStatusInOrderByUpdatedAtAsc(
                        Set.of("pending", "processing")))
                .extracting(CistAiAnalysisEntity::getAnalysisId)
                .containsExactly(firstDaily.getAnalysisId(), secondDaily.getAnalysisId());
    }

    private CistAiAnalysisEntity analysis(UUID sessionId, String status, UUID baselineAnalysisId, Instant now) {
        UUID analysisId = UUID.randomUUID();
        CistAiAnalysisEntity analysis = new CistAiAnalysisEntity(
                analysisId, sessionId, status, "sync-test-" + analysisId, "0".repeat(64), "{}", now, now);
        if (baselineAnalysisId != null) {
            analysis.linkBaselineAnalysis(baselineAnalysisId);
        }
        return analysis;
    }
}
