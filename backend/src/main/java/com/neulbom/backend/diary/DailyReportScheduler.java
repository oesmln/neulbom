package com.neulbom.backend.diary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates the previous day's summary and diary after the Asia/Seoul day closes.
 * The repository uniqueness constraints make retries safe when the process was
 * down at midnight; a later run simply receives the existing summary/job.
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyReportScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final DailyDiaryGenerator dailyDiaryGenerator;
    private final Clock clock;

    public DailyReportScheduler(
            UserRepository userRepository,
            DailyDiaryGenerator dailyDiaryGenerator,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.dailyDiaryGenerator = dailyDiaryGenerator;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.scheduler.daily-report-cron:0 5 0 * * *}", zone = "Asia/Seoul")
    public void createPreviousDayReports() {
        processDate(LocalDate.now(clock.withZone(BUSINESS_ZONE)).minusDays(1));
    }

    void processDate(LocalDate targetDate) {
        userRepository.findAll().stream()
                .filter(UserEntity::isActive)
                .filter(user -> "elder".equals(user.getRole()))
                .forEach(user -> processUser(user, targetDate));
    }

    private void processUser(UserEntity user, LocalDate targetDate) {
        try {
            // 전날 정서 문답을 Gemini 요약으로 묶어 일기 본문까지 만든다.
            dailyDiaryGenerator.generate(user.getId(), targetDate);
        } catch (RuntimeException exception) {
            // One user's provider or data failure must not prevent the rest of
            // the elder accounts from receiving their next report.
            log.warn("일일 리포트 생성 실패 user_id={} target_date={} reason={}",
                    user.getId(), targetDate, exception.getClass().getSimpleName());
        }
    }
}
