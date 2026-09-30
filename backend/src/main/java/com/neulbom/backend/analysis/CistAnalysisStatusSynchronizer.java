package com.neulbom.backend.analysis;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import com.neulbom.backend.session.SessionRepository;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Synchronizes in-flight full CIST analyses and recovers missing create requests. */
@Component
@ConditionalOnProperty(
        prefix = "app.scheduler",
        name = {"enabled", "cist-analysis-sync-enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class CistAnalysisStatusSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(CistAnalysisStatusSynchronizer.class);
    private static final Set<String> IN_FLIGHT_STATUSES = Set.of("pending", "processing");

    private final CistAiAnalysisRepository analysisRepository;
    private final SessionRepository sessionRepository;
    private final CistAiAnalysisService analysisService;
    private final Clock clock;

    public CistAnalysisStatusSynchronizer(
            CistAiAnalysisRepository analysisRepository,
            SessionRepository sessionRepository,
            CistAiAnalysisService analysisService,
            Clock clock
    ) {
        this.analysisRepository = analysisRepository;
        this.sessionRepository = sessionRepository;
        this.analysisService = analysisService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.cist-analysis-sync-delay-ms:10000}")
    public void synchronizeInFlightAnalyses() {
        analysisRepository
                .findTop100ByBaselineAnalysisIdIsNullAndStatusInOrderByUpdatedAtAsc(IN_FLIGHT_STATUSES)
                .forEach(this::synchronizeOne);
    }

    /** Recovers a finished exam whose initial AI create request never persisted an analysis. */
    @Scheduled(fixedDelayString = "${app.scheduler.cist-analysis-recovery-delay-ms:60000}")
    public void createMissingAnalyses() {
        sessionRepository.findEndedCistSessionsMissingAnalysis(
                        clock.instant().minus(Duration.ofSeconds(30)), PageRequest.of(0, 100))
                .forEach(session -> {
                    try {
                        analysisService.createAnalysis(session.getUserId(), session.getId());
                    } catch (RuntimeException exception) {
                        log.warn("CIST 분석 생성 복구 실패 session_id={} reason={}",
                                session.getId(), exception.getClass().getSimpleName());
                    }
                });
    }

    private void synchronizeOne(CistAiAnalysisEntity analysis) {
        try {
            var session = sessionRepository.findById(analysis.getSessionId())
                    .orElseThrow(() -> new IllegalStateException("CIST session missing"));
            analysisService.refreshAnalysis(session.getUserId(), session.getId());
        } catch (RuntimeException exception) {
            log.warn("CIST 분석 상태 동기화 실패 analysis_id={} session_id={} reason={}",
                    analysis.getAnalysisId(), analysis.getSessionId(), exception.getClass().getSimpleName());
        }
    }
}
