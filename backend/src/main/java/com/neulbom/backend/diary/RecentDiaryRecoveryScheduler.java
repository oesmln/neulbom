package com.neulbom.backend.diary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 종료 후 일기 생성에 실패한 최근 정서 문답 세션을 다시 처리한다. */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RecentDiaryRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecentDiaryRecoveryScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int LOOKBACK_DAYS = 7;

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final DailyDiaryGenerator dailyDiaryGenerator;
    private final Clock clock;

    public RecentDiaryRecoveryScheduler(
            SessionRepository sessionRepository,
            UserRepository userRepository,
            DiaryRepository diaryRepository,
            DailyDiaryGenerator dailyDiaryGenerator,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.diaryRepository = diaryRepository;
        this.dailyDiaryGenerator = dailyDiaryGenerator;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void recoverRecentDiaryDays() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Instant from = today.minusDays(LOOKBACK_DAYS).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = clock.instant();
        for (SessionEntity session : sessionRepository.findEndedEmotionalQaSessionsStartedBetween(from, to)) {
            if (diaryRepository.findFirstBySessionIdAndSourceTypeOrderByCreatedAtAsc(session.getId(), "session").isPresent()) continue;
            if (userRepository.findById(session.getUserId())
                    .filter(UserEntity::isActive)
                    .filter(user -> "elder".equals(user.getRole()))
                    .isEmpty()) {
                continue;
            }
            try {
                dailyDiaryGenerator.generateForSession(session.getId());
            } catch (RuntimeException exception) {
                log.warn("누락된 일기 재생성 실패 user_id={} session_id={} reason={}",
                        session.getUserId(), session.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
