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

/** Synchronizes in-flight daily analyses and retries creation after the baseline is ready. */
@Component
@ConditionalOnProperty(
        prefix = "app.scheduler",
        name = {"enabled", "daily-cognitive-sync-enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class DailyCognitiveAnalysisStatusSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(DailyCognitiveAnalysisStatusSynchronizer.class);
    private static final Set<String> IN_FLIGHT_STATUSES = Set.of("pending", "processing");

    private final CistAiAnalysisRepository analysisRepository;
    private final SessionRepository sessionRepository;
    private final CistAiAnalysisService analysisService;
    private final Clock clock;

    public DailyCognitiveAnalysisStatusSynchronizer(
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

    @Scheduled(fixedDelayString = "${app.scheduler.daily-cognitive-sync-delay-ms:5000}")
    public void synchronizeInFlightAnalyses() {
        analysisRepository
                .findTop100ByBaselineAnalysisIdIsNotNullAndStatusInOrderByUpdatedAtAsc(IN_FLIGHT_STATUSES)
                .forEach(this::synchronizeOne);
    }

    /** Retries sessions that ended before their CIST baseline became available. */
    @Scheduled(fixedDelayString = "${app.scheduler.daily-cognitive-recovery-delay-ms:60000}")
    public void createMissingAnalyses() {
        sessionRepository.findEndedDailySessionsMissingAnalysis(
                        clock.instant().minus(Duration.ofSeconds(30)), PageRequest.of(0, 100))
                .forEach(session -> {
                    try {
                        analysisService.createDailyAnalysis(session.getUserId(), session.getId());
                    } catch (RuntimeException exception) {
                        log.warn("일상 인지 분석 생성 복구 실패 session_id={} reason={}",
                                session.getId(), exception.getClass().getSimpleName());
                    }
                });
    }

    private void synchronizeOne(CistAiAnalysisEntity analysis) {
        var session = sessionRepository.findById(analysis.getSessionId()).orElse(null);
        if (session == null) {
            log.warn("일상 인지 분석 상태 동기화 세션 누락 analysis_id={} session_id={}",
                    analysis.getAnalysisId(), analysis.getSessionId());
            return;
        }
        try {
            analysisService.refreshDailyAnalysis(session.getUserId(), session.getId());
        } catch (RuntimeException exception) {
            log.warn("일상 인지 분석 상태 동기화 실패 analysis_id={} session_id={} reason={}",
                    analysis.getAnalysisId(), analysis.getSessionId(), exception.getClass().getSimpleName());
        }
    }
}
