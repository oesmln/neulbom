package com.neulbom.backend.analysis;

import com.neulbom.backend.session.SessionEndedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts the partial CIST update after an emotional-QA session is committed. */
@Component
public class DailyCognitiveAnalysisTrigger {

    private static final Logger log = LoggerFactory.getLogger(DailyCognitiveAnalysisTrigger.class);
    private static final String DAILY_SESSION_TYPE = "emotional_qa";

    private final CistAiAnalysisService analysisService;

    public DailyCognitiveAnalysisTrigger(CistAiAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionEnded(SessionEndedEvent event) {
        if (!DAILY_SESSION_TYPE.equals(event.sessionType())) {
            return;
        }
        try {
            analysisService.createDailyAnalysis(event.userId(), event.sessionId());
        } catch (RuntimeException exception) {
            // Session completion is already committed. Allow the client to retry via the daily-analysis endpoint.
            log.warn("일상 인지 분석 시작 실패 user_id={} session_id={} reason={}",
                    event.userId(), event.sessionId(), exception.getClass().getSimpleName());
        }
    }
}
