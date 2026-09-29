package com.neulbom.backend.diary;

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

class RecentDiaryRecoverySchedulerTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T04:00:00Z"), ZoneOffset.UTC);
    @Test
    void recoversEveryMissingSessionDiaryOnTheSameDay() {
        SessionRepository sessions = mock(SessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        DiaryRepository diaries = mock(DiaryRepository.class);
        DailyDiaryGenerator generator = mock(DailyDiaryGenerator.class);
        UUID userId = UUID.randomUUID();
        SessionEntity first = session(userId, Instant.parse("2026-09-28T01:00:00Z"));
        SessionEntity second = session(userId, Instant.parse("2026-09-28T06:00:00Z"));
        when(sessions.findEndedEmotionalQaSessionsStartedBetween(windowStart(), windowEnd()))
                .thenReturn(List.of(first, second));
        activeElder(users, userId);
        new RecentDiaryRecoveryScheduler(sessions, users, diaries, generator, CLOCK).recoverRecentDiaryDays();

        verify(generator).generateForSession(first.getId());
        verify(generator).generateForSession(second.getId());
    }

    @Test
    void skipsExistingSessionDiary() {
        SessionRepository sessions = mock(SessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        DiaryRepository diaries = mock(DiaryRepository.class);
        DailyDiaryGenerator generator = mock(DailyDiaryGenerator.class);
        UUID completedUser = UUID.randomUUID();
        UUID untranscribedUser = UUID.randomUUID();
        SessionEntity completed = session(completedUser, Instant.parse("2026-09-28T01:00:00Z"));
        SessionEntity untranscribed = session(untranscribedUser, Instant.parse("2026-09-28T03:00:00Z"));
        when(sessions.findEndedEmotionalQaSessionsStartedBetween(windowStart(), windowEnd()))
                .thenReturn(List.of(completed, untranscribed));
        when(diaries.findFirstBySessionIdAndSourceTypeOrderByCreatedAtAsc(completed.getId(), "session"))
                .thenReturn(Optional.of(mock(DiaryEntity.class)));
        activeElder(users, untranscribedUser);

        new RecentDiaryRecoveryScheduler(sessions, users, diaries, generator, CLOCK).recoverRecentDiaryDays();

        verify(generator, never()).generateForSession(completed.getId());
        verify(generator).generateForSession(untranscribed.getId());
    }

    private SessionEntity session(UUID userId, Instant startedAt) {
        SessionEntity session = mock(SessionEntity.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getUserId()).thenReturn(userId);
        when(session.getStartedAt()).thenReturn(startedAt);
        return session;
    }

    private void activeElder(UserRepository users, UUID userId) {
        UserEntity user = mock(UserEntity.class);
        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn("elder");
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    private Instant windowStart() {
        return LocalDate.of(2026, 9, 22).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    private Instant windowEnd() {
        return CLOCK.instant();
    }
}
