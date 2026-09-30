package com.neulbom.backend.analysis;

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

/**
 * 종료 직후 일상 인지 분석 생성에 실패한 최근 정서 문답 세션을 다시 접수한다.
 *
 * <p>{@link DailyCognitiveAnalysisTrigger}는 세션 종료 커밋 직후에만 동작하고 실패를 로그로만
 * 남긴다. 분석 행이 만들어지지 않으면 {@link DailyCognitiveAnalysisStatusSynchronizer}도 진행 중
 * 분석만 폴링하므로 그 세션을 다시 집지 않는다. 기준 CIST 분석이 나중에 완료된 경우처럼
 * 뒤늦게 조건이 갖춰진 세션을 여기서 복구한다.
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DailyCognitiveAnalysisRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyCognitiveAnalysisRecoveryScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int LOOKBACK_DAYS = 7;

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final CistAiAnalysisRepository analysisRepository;
    private final CistAiAnalysisService analysisService;
    private final Clock clock;

    public DailyCognitiveAnalysisRecoveryScheduler(
            SessionRepository sessionRepository,
            UserRepository userRepository,
            CistAiAnalysisRepository analysisRepository,
            CistAiAnalysisService analysisService,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.analysisRepository = analysisRepository;
        this.analysisService = analysisService;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 90_000, fixedDelay = 3_600_000)
    public void recoverMissingDailyAnalyses() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        Instant from = today.minusDays(LOOKBACK_DAYS).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = clock.instant();
        for (SessionEntity session : sessionRepository.findEndedEmotionalQaSessionsStartedBetween(from, to)) {
            if (analysisRepository.findBySessionId(session.getId()).isPresent()) {
                continue;
            }
            if (userRepository.findById(session.getUserId())
                    .filter(UserEntity::isActive)
                    .filter(user -> "elder".equals(user.getRole()))
                    .isEmpty()) {
                continue;
            }
            try {
                analysisService.createDailyAnalysis(session.getUserId(), session.getId());
            } catch (RuntimeException exception) {
                log.warn("누락된 일상 인지 분석 재접수 실패 user_id={} session_id={} reason={}",
                        session.getUserId(), session.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
