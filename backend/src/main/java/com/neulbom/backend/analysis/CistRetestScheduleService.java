package com.neulbom.backend.analysis;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.analysis.api.CistRetestScheduleResponse;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CistRetestScheduleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> FULL_CIST_TYPES = Set.of("cist", "baseline", "onboarding");

    private final SessionRepository sessions;
    private final Clock clock;

    public CistRetestScheduleService(SessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CistRetestScheduleResponse getSchedule(UUID userId) {
        var completedSessions = sessions.findAllByUserIdOrderByStartedAtDesc(userId).stream()
                .filter(session -> FULL_CIST_TYPES.contains(session.getSessionType()))
                .filter(session -> SessionEntity.ENDED.equals(session.getStatus()) && session.getEndedAt() != null)
                .filter(session -> session.getTotalQuestions() > 0
                        && session.getAnsweredCount() >= session.getTotalQuestions())
                .toList();
        if (completedSessions.isEmpty()) {
            return emptySchedule();
        }
        SessionEntity latest = completedSessions.stream()
                .max(Comparator.comparing(SessionEntity::getEndedAt)
                        .thenComparing(session -> session.getId().toString()))
                .orElseThrow();
        LocalDate completedDate = latest.getEndedAt().atZone(BUSINESS_ZONE).toLocalDate();
        LocalDate nextDueDate = completedDate.plusMonths(3);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return new CistRetestScheduleResponse(
                latest.getId(), completedDate, nextDueDate, !today.isBefore(nextDueDate), BUSINESS_ZONE.getId());
    }

    private CistRetestScheduleResponse emptySchedule() {
        return new CistRetestScheduleResponse(null, null, null, false, BUSINESS_ZONE.getId());
    }
}
