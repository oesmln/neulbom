package com.neulbom.backend.analysis;

import java.util.Set;

import com.neulbom.backend.session.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Synchronizes in-flight full CIST analyses without depending on the result screen polling. */
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

    public CistAnalysisStatusSynchronizer(
            CistAiAnalysisRepository analysisRepository,
            SessionRepository sessionRepository,
            CistAiAnalysisService analysisService
    ) {
        this.analysisRepository = analysisRepository;
        this.sessionRepository = sessionRepository;
        this.analysisService = analysisService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.cist-analysis-sync-delay-ms:10000}")
    public void synchronizeInFlightAnalyses() {
        analysisRepository
                .findTop100ByBaselineAnalysisIdIsNullAndStatusInOrderByUpdatedAtAsc(IN_FLIGHT_STATUSES)
                .forEach(this::synchronizeOne);
        analysisRepository
                .findTop100ByBaselineAnalysisIdIsNullAndStatusAndReasonCodeAndRetryCountOrderByUpdatedAtAsc(
                        "failed", "INTERNAL_ERROR", 0)
                .forEach(this::restartOne);
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

    private void restartOne(CistAiAnalysisEntity analysis) {
        try {
            var session = sessionRepository.findById(analysis.getSessionId())
                    .orElseThrow(() -> new IllegalStateException("CIST session missing"));
            analysisService.retryAnalysis(session.getUserId(), session.getId());
        } catch (RuntimeException exception) {
            log.warn("CIST 분석 재처리 실패 analysis_id={} session_id={} reason={}",
                    analysis.getAnalysisId(), analysis.getSessionId(), exception.getClass().getSimpleName());
        }
    }
}
