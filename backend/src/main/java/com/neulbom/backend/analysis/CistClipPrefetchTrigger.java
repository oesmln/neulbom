package com.neulbom.backend.analysis;

import com.neulbom.backend.session.CistAnswerSavedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Best-effort prefetch after answer commit; it never changes answer persistence. */
@Component
public class CistClipPrefetchTrigger {
    private static final Logger log = LoggerFactory.getLogger(CistClipPrefetchTrigger.class);
    private final CistAiAnalysisService analysisService;

    public CistClipPrefetchTrigger(CistAiAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Async("cistClipPrefetchExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnswerSaved(CistAnswerSavedEvent event) {
        try {
            analysisService.prefetchClipForAnswer(event.answerId());
        } catch (RuntimeException error) {
            log.warn("CIST clip prefetch request failed answer_id={} reason={}",
                    event.answerId(), error.getClass().getSimpleName());
        }
    }
}
