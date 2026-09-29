package com.neulbom.backend.analysis;

import static com.neulbom.backend.analysis.integration.aiserver.AiServerContractFixtures.featureSnapshot;
import static com.neulbom.backend.analysis.integration.aiserver.AiServerContractFixtures.fullQuestionResults;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.neulbom.backend.analysis.integration.aiserver.AiServerClient;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CistAnalysisStatusSynchronizerIntegrationTest {

    @Autowired private CistAiAnalysisService analysisService;
    @Autowired private CistAiAnalysisRepository analyses;
    @Autowired private CognitiveFeatureSnapshotRepository snapshots;
    @Autowired private SessionRepository sessions;
    @Autowired private UserRepository users;

    @MockitoBean private AiServerClient aiServerClient;

    @Test
    void completesProcessingCistWithoutResultScreenPollingAndCreatesOneBaselineSnapshot() {
        Instant startedAt = Instant.parse("2026-09-29T00:00:00Z");
        UserEntity elder = elder(startedAt);
        SessionEntity cist = sessions.save(new SessionEntity(
                UUID.randomUUID(), elder.getId(), "cist", 17, "{}", false, startedAt));
        CistAiAnalysisEntity fullAnalysis = analysis(cist, "processing", startedAt);

        SessionEntity daily = sessions.save(new SessionEntity(
                UUID.randomUUID(), elder.getId(), "emotional_qa", 2, "{}", false, startedAt.plusSeconds(60)));
        CistAiAnalysisEntity dailyAnalysis = analysis(daily, "processing", startedAt.plusSeconds(60));
        dailyAnalysis.linkBaselineAnalysis(fullAnalysis.getAnalysisId());
        analyses.saveAndFlush(dailyAnalysis);

        BigDecimal score = new BigDecimal("0.4234567891");
        var features = new AiServerContracts.FusionFeatures(
                new BigDecimal("0.1"), new BigDecimal("0.2"),
                new BigDecimal("0.3"), new BigDecimal("0.4"));
        var questionResults = fullQuestionResults();
        var result = new AiServerContracts.FinalAnalysisResult(
                AiServerContracts.QUESTION_SET_VERSION,
                AiServerContracts.WRONG_EVENT_RULE_VERSION,
                AiServerContracts.FUSION_MODEL_VERSION,
                score,
                new BigDecimal("0.38592870327757767"),
                new BigDecimal("0.8061380697921943"),
                AiServerContracts.THRESHOLD_VERSION,
                true,
                "monitoring_needed",
                features,
                featureSnapshot(questionResults, score, features),
                questionResults);
        when(aiServerClient.getAnalysis(fullAnalysis.getAnalysisId())).thenReturn(
                new AiServerContracts.AnalysisStatusResponse(
                        fullAnalysis.getAnalysisId(), cist.getId(), "completed",
                        startedAt, startedAt.plusSeconds(120), false, null, List.of(), result));

        assertThat(snapshots.findBySourceAnalysisId(fullAnalysis.getAnalysisId())).isEmpty();
        synchronize();

        CistAiAnalysisEntity stored = analyses.findById(fullAnalysis.getAnalysisId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("completed");
        assertThat(stored.getModelScore()).isEqualByComparingTo(score);
        CognitiveFeatureSnapshotEntity baseline = snapshots
                .findBySourceAnalysisId(fullAnalysis.getAnalysisId()).orElseThrow();
        assertThat(baseline.getUserId()).isEqualTo(elder.getId());
        assertThat(baseline.getSourceSessionId()).isEqualTo(cist.getId());
        assertThat(baseline.getBaselineModelScore()).isEqualByComparingTo(score);
        assertThat(analyses.findById(dailyAnalysis.getAnalysisId()).orElseThrow().getStatus())
                .isEqualTo("processing");
        verify(aiServerClient, never()).getAnalysis(dailyAnalysis.getAnalysisId());

        synchronize();
        verify(aiServerClient, times(1)).getAnalysis(fullAnalysis.getAnalysisId());
        assertThat(snapshots.findAllByUserId(elder.getId())).hasSize(1);
    }

    @Test
    void marksFailedCistAsFailedWithoutCreatingBaselineSnapshot() {
        Instant startedAt = Instant.parse("2026-09-29T01:00:00Z");
        UserEntity elder = elder(startedAt);
        SessionEntity cist = sessions.save(new SessionEntity(
                UUID.randomUUID(), elder.getId(), "cist", 17, "{}", false, startedAt));
        CistAiAnalysisEntity analysis = analysis(cist, "processing", startedAt);
        when(aiServerClient.getAnalysis(analysis.getAnalysisId())).thenReturn(
                new AiServerContracts.AnalysisStatusResponse(
                        analysis.getAnalysisId(), cist.getId(), "failed",
                        startedAt, startedAt.plusSeconds(120), false, "INTERNAL_ERROR", List.of(), null));

        synchronize();

        assertThat(analyses.findById(analysis.getAnalysisId()).orElseThrow().getStatus())
                .isEqualTo("failed");
        assertThat(snapshots.findBySourceAnalysisId(analysis.getAnalysisId())).isEmpty();
    }

    private UserEntity elder(Instant now) {
        return users.save(new UserEntity(
                UUID.randomUUID(), "cist-scheduler-" + UUID.randomUUID() + "@example.com",
                null, "동기화 테스트", "elder", LocalDate.of(1945, 1, 1),
                "80s_plus", "female", null, true, now, now));
    }

    private void synchronize() {
        new CistAnalysisStatusSynchronizer(analyses, sessions, analysisService)
                .synchronizeInFlightAnalyses();
    }

    private CistAiAnalysisEntity analysis(SessionEntity session, String status, Instant now) {
        UUID analysisId = UUID.randomUUID();
        return analyses.saveAndFlush(new CistAiAnalysisEntity(
                analysisId, session.getId(), status, "scheduler-create-" + analysisId,
                "0".repeat(64), "{}", now, now));
    }
}
