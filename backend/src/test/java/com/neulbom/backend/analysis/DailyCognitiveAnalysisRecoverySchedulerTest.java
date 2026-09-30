package com.neulbom.backend.analysis;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;

class DailyCognitiveAnalysisRecoverySchedulerTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void requestsAnalysisForEveryEndedSessionWithoutOne() {
        Fixture fixture = new Fixture();
        UUID userId = UUID.randomUUID();
        SessionEntity first = fixture.session(userId, Instant.parse("2026-09-28T01:00:00Z"));
        SessionEntity second = fixture.session(userId, Instant.parse("2026-09-28T06:00:00Z"));
        fixture.endedSessions(first, second);
        fixture.activeElder(userId);

        fixture.scheduler().recoverMissingDailyAnalyses();

        verify(fixture.analysisService).createDailyAnalysis(userId, first.getId());
        verify(fixture.analysisService).createDailyAnalysis(userId, second.getId());
    }

    @Test
    void skipsSessionThatAlreadyHasAnAnalysis() {
        Fixture fixture = new Fixture();
        UUID userId = UUID.randomUUID();
        SessionEntity analyzed = fixture.session(userId, Instant.parse("2026-09-28T01:00:00Z"));
        SessionEntity missing = fixture.session(userId, Instant.parse("2026-09-28T03:00:00Z"));
        UUID analyzedId = analyzed.getId();
        fixture.endedSessions(analyzed, missing);
        fixture.activeElder(userId);
        when(fixture.analysisRepository.findBySessionId(analyzedId))
                .thenReturn(Optional.of(mock(CistAiAnalysisEntity.class)));

        fixture.scheduler().recoverMissingDailyAnalyses();

        verify(fixture.analysisService, never()).createDailyAnalysis(userId, analyzed.getId());
        verify(fixture.analysisService).createDailyAnalysis(userId, missing.getId());
    }

    @Test
    void keepsGoingAfterOneSessionFails() {
        Fixture fixture = new Fixture();
        UUID userId = UUID.randomUUID();
        SessionEntity failing = fixture.session(userId, Instant.parse("2026-09-28T01:00:00Z"));
        SessionEntity following = fixture.session(userId, Instant.parse("2026-09-28T03:00:00Z"));
        UUID failingId = failing.getId();
        fixture.endedSessions(failing, following);
        fixture.activeElder(userId);
        doThrow(new IllegalStateException("기준 분석 없음"))
                .when(fixture.analysisService).createDailyAnalysis(userId, failingId);

        fixture.scheduler().recoverMissingDailyAnalyses();

        verify(fixture.analysisService).createDailyAnalysis(userId, following.getId());
    }

    @Test
    void skipsSessionOfInactiveUser() {
        Fixture fixture = new Fixture();
        UUID userId = UUID.randomUUID();
        SessionEntity session = fixture.session(userId, Instant.parse("2026-09-28T01:00:00Z"));
        fixture.endedSessions(session);
        UserEntity user = mock(UserEntity.class);
        when(user.isActive()).thenReturn(false);
        when(fixture.userRepository.findById(userId)).thenReturn(Optional.of(user));


        fixture.scheduler().recoverMissingDailyAnalyses();

        verify(fixture.analysisService, never()).createDailyAnalysis(userId, session.getId());
    }

    private static final class Fixture {
        private final SessionRepository sessionRepository = mock(SessionRepository.class);
        private final UserRepository userRepository = mock(UserRepository.class);
        private final CistAiAnalysisRepository analysisRepository = mock(CistAiAnalysisRepository.class);
        private final CistAiAnalysisService analysisService = mock(CistAiAnalysisService.class);

        private DailyCognitiveAnalysisRecoveryScheduler scheduler() {
            return new DailyCognitiveAnalysisRecoveryScheduler(
                    sessionRepository, userRepository, analysisRepository, analysisService, CLOCK);
        }

        private void endedSessions(SessionEntity... sessions) {
            when(sessionRepository.findEndedEmotionalQaSessionsStartedBetween(windowStart(), CLOCK.instant()))
                    .thenReturn(List.of(sessions));
        }

        private SessionEntity session(UUID userId, Instant startedAt) {
            SessionEntity session = mock(SessionEntity.class);
            when(session.getId()).thenReturn(UUID.randomUUID());
            when(session.getUserId()).thenReturn(userId);
            when(session.getStartedAt()).thenReturn(startedAt);
            return session;
        }

        private void activeElder(UUID userId) {
            UserEntity user = mock(UserEntity.class);
            when(user.isActive()).thenReturn(true);
            when(user.getRole()).thenReturn("elder");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        }

        private Instant windowStart() {
            return LocalDate.of(2026, 9, 22).atStartOfDay(BUSINESS_ZONE).toInstant();
        }
    }
}
