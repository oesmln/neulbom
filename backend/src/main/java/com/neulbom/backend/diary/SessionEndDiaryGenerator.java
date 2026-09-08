package com.neulbom.backend.diary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import com.neulbom.backend.session.SessionEndedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 정서 문답 세션이 끝나자마자 그날의 일기를 만든다.
 *
 * 기본 흐름은 {@link DailyReportScheduler}가 자정 이후 전날 것을 만드는 것이지만, 로컬에서
 * "대화 → 일기"를 바로 확인하려고 {@code app.diary.generate-on-session-end=true}일 때만 켠다.
 * 당일 문답을 {@link DailyDiaryGenerator}로 넘겨 Gemini 요약 → 일기까지 만든다. 같은 날 일기가 이미 있으면 {@link DiaryService}가 기존 것을 돌려주므로
 * 하루 한 편 규칙은 그대로다. 실패해도 세션 종료 응답에는 영향을 주지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "app.diary", name = "generate-on-session-end", havingValue = "true")
public class SessionEndDiaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(SessionEndDiaryGenerator.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final String DIARY_SESSION_TYPE = "emotional_qa";

    private final DailyDiaryGenerator dailyDiaryGenerator;
    private final Clock clock;

    public SessionEndDiaryGenerator(DailyDiaryGenerator dailyDiaryGenerator, Clock clock) {
        this.dailyDiaryGenerator = dailyDiaryGenerator;
        this.clock = clock;
        log.info("세션 종료 즉시 일기 생성이 켜져 있습니다 (app.diary.generate-on-session-end=true)");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionEnded(SessionEndedEvent event) {
        if (!DIARY_SESSION_TYPE.equals(event.sessionType())) {
            return;
        }
        try {
            dailyDiaryGenerator.generate(event.userId(), LocalDate.now(clock.withZone(BUSINESS_ZONE)));
        } catch (RuntimeException exception) {
            // 세션 종료 자체는 이미 커밋됐다. 요약/일기 실패는 로그만 남기고 자정 스케줄러가 다시 시도한다.
            log.warn("세션 종료 즉시 일기 생성 실패 user_id={} session_id={} reason={}",
                    event.userId(), event.sessionId(), exception.getClass().getSimpleName());
        }
    }

}
