package com.neulbom.backend.diary;

import com.neulbom.backend.session.SessionEndedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 정서 문답 세션이 끝나자마자 해당 세션의 일기를 만든다.
 */
@Component
@ConditionalOnProperty(prefix = "app.diary", name = "generate-on-session-end", havingValue = "true", matchIfMissing = true)
public class SessionEndDiaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(SessionEndDiaryGenerator.class);
    private static final String DIARY_SESSION_TYPE = "emotional_qa";

    private final DailyDiaryGenerator dailyDiaryGenerator;
    public SessionEndDiaryGenerator(DailyDiaryGenerator dailyDiaryGenerator) {
        this.dailyDiaryGenerator = dailyDiaryGenerator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionEnded(SessionEndedEvent event) {
        if (!DIARY_SESSION_TYPE.equals(event.sessionType())) {
            return;
        }
        try {
            dailyDiaryGenerator.generateForSession(event.sessionId());
        } catch (RuntimeException exception) {
            // 세션 종료는 이미 커밋됐다. 실패한 세션은 복구 스케줄러가 다시 시도한다.
            log.warn("세션 종료 즉시 일기 생성 실패 user_id={} session_id={} reason={}",
                    event.userId(), event.sessionId(), exception.getClass().getSimpleName());
        }
    }

}
