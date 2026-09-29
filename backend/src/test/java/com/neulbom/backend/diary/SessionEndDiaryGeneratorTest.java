package com.neulbom.backend.diary;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import com.neulbom.backend.session.SessionEndedEvent;
import org.junit.jupiter.api.Test;

class SessionEndDiaryGeneratorTest {

    @Test
    void generatesDiaryForEndedConversationOnly() {
        DailyDiaryGenerator generator = mock(DailyDiaryGenerator.class);
        SessionEndDiaryGenerator listener = new SessionEndDiaryGenerator(generator);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();

        listener.onSessionEnded(new SessionEndedEvent(conversationId, userId, "emotional_qa"));
        listener.onSessionEnded(new SessionEndedEvent(screeningId, userId, "cist"));

        verify(generator).generateForSession(conversationId);
        verify(generator, never()).generateForSession(screeningId);
    }

    @Test
    void diaryFailureDoesNotUndoEndedSession() {
        DailyDiaryGenerator generator = mock(DailyDiaryGenerator.class);
        SessionEndDiaryGenerator listener = new SessionEndDiaryGenerator(generator);
        UUID sessionId = UUID.randomUUID();
        doThrow(new RuntimeException("provider unavailable")).when(generator).generateForSession(sessionId);

        listener.onSessionEnded(new SessionEndedEvent(sessionId, UUID.randomUUID(), "emotional_qa"));

        verify(generator).generateForSession(sessionId);
    }
}
