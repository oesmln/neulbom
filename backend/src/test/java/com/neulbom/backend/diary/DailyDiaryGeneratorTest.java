package com.neulbom.backend.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.neulbom.backend.analysis.AnalysisService;
import com.neulbom.backend.analysis.SessionSummaryEntity;
import com.neulbom.backend.analysis.SessionSummaryRepository;
import com.neulbom.backend.analysis.api.QaPair;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.diary.api.DiaryFromSessionRequest;
import com.neulbom.backend.diary.api.DiaryResponse;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.session.AnswerEntity;
import com.neulbom.backend.session.AnswerRepository;
import com.neulbom.backend.session.QuestionEntity;
import com.neulbom.backend.session.QuestionRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DailyDiaryGeneratorTest {

    @Test
    void doesNotSilentlyDropAnAnsweredGeminiQuestionWhileItsTranscriptIsPending() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        AnswerRepository answers = mock(AnswerRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        AnswerEntity answer = mock(AnswerEntity.class);
        QuestionEntity question = mock(QuestionEntity.class);
        when(answer.getQuestionId()).thenReturn(questionId);
        when(answer.getAnswerText()).thenReturn(null);
        when(answer.getRecordingId()).thenReturn(UUID.randomUUID());
        when(answers.findAllBySessionIdOrderByAnsweredAtAsc(sessionId)).thenReturn(List.of(answer));
        when(questions.findById(questionId)).thenReturn(Optional.of(question));
        when(question.getQuestionSource()).thenReturn("gemini");
        DailyDiaryGenerator generator = new DailyDiaryGenerator(
                mock(SessionRepository.class), mock(SessionSummaryRepository.class), answers, questions,
                mock(TranscriptRepository.class), mock(AnalysisService.class), mock(DiaryService.class), mock(DiaryRepository.class),
                mock(DailyDiaryWriter.class));

        assertThatThrownBy(() -> generator.qaPairs(sessionId))
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void createsOneDiaryForEachEndedSession() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 28);
        SessionEntity morning = session(userId, Instant.parse("2026-09-28T01:00:00Z"));
        SessionEntity evening = session(userId, Instant.parse("2026-09-28T11:00:00Z"));
        SessionRepository sessions = mock(SessionRepository.class);
        SessionSummaryRepository summaries = mock(SessionSummaryRepository.class);
        AnswerRepository answers = mock(AnswerRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        AnalysisService analysis = mock(AnalysisService.class);
        DiaryService diaries = mock(DiaryService.class);
        DiaryRepository diaryRepository = mock(DiaryRepository.class);
        DailyDiaryWriter writer = mock(DailyDiaryWriter.class);
        when(sessions.findAllByUserIdOrderByStartedAtDesc(userId)).thenReturn(List.of(evening, morning));
        when(sessions.findById(morning.getId())).thenReturn(Optional.of(morning));
        when(sessions.findById(evening.getId())).thenReturn(Optional.of(evening));
        answer(answers, questions, morning, "점심에 무엇을 드셨어요?", "딸과 비빔밥을 먹었어요.");
        answer(answers, questions, evening, "오늘 누구와 이야기하셨어요?", "친구와 통화했어요.");
        when(summaries.findBySessionId(morning.getId())).thenReturn(Optional.of(mock(SessionSummaryEntity.class)));
        when(summaries.findBySessionId(evening.getId())).thenReturn(Optional.of(mock(SessionSummaryEntity.class)));
        when(writer.write(eq(date), any())).thenReturn("그날의 일기");
        UUID morningId = morning.getId();
        when(diaries.createFromSession(eq(userId), any())).thenReturn(new DiaryResponse(
                UUID.randomUUID(), userId, "session", "오늘의 이야기", "그날의 일기", null, morningId, null,
                null, null, Instant.now(), Instant.now(), Instant.now()));

        DailyDiaryGenerator generator = new DailyDiaryGenerator(
                sessions, summaries, answers, questions, mock(TranscriptRepository.class), analysis, diaries, diaryRepository, writer);

        generator.generateForSession(morning.getId());
        generator.generateForSession(evening.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<QaPair>>> conversations = ArgumentCaptor.forClass(List.class);
        verify(writer, org.mockito.Mockito.times(2)).write(eq(date), conversations.capture());
        assertThat(conversations.getAllValues()).hasSize(2);
        assertThat(conversations.getAllValues().get(0).get(0)).extracting(QaPair::answer)
                .containsExactly("딸과 비빔밥을 먹었어요.");
        assertThat(conversations.getAllValues().get(1).get(0)).extracting(QaPair::answer)
                .containsExactly("친구와 통화했어요.");
        ArgumentCaptor<DiaryFromSessionRequest> request = ArgumentCaptor.forClass(DiaryFromSessionRequest.class);
        verify(diaries, org.mockito.Mockito.times(2)).createFromSession(eq(userId), request.capture());
        assertThat(request.getAllValues()).extracting(DiaryFromSessionRequest::sessionId)
                .containsExactly(morning.getId(), evening.getId());
    }

    private SessionEntity session(UUID userId, Instant startedAt) {
        SessionEntity session = mock(SessionEntity.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getUserId()).thenReturn(userId);
        when(session.getSessionType()).thenReturn("emotional_qa");
        when(session.getStatus()).thenReturn(SessionEntity.ENDED);
        when(session.getStartedAt()).thenReturn(startedAt);
        return session;
    }

    private void answer(
            AnswerRepository answers,
            QuestionRepository questions,
            SessionEntity session,
            String questionText,
            String answerText
    ) {
        UUID questionId = UUID.randomUUID();
        AnswerEntity answer = mock(AnswerEntity.class);
        when(answer.getQuestionId()).thenReturn(questionId);
        when(answer.getAnswerText()).thenReturn(answerText);
        when(answers.findAllBySessionIdOrderByAnsweredAtAsc(session.getId())).thenReturn(List.of(answer));
        QuestionEntity question = mock(QuestionEntity.class);
        when(question.getId()).thenReturn(questionId);
        when(question.getContent()).thenReturn(questionText);
        when(question.getQuestionType()).thenReturn("emotion");
        when(question.getQuestionSource()).thenReturn("gemini");
        when(questions.findById(questionId)).thenReturn(Optional.of(question));
    }
}
