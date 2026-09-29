package com.neulbom.backend.diary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.neulbom.backend.analysis.AnalysisService;
import com.neulbom.backend.analysis.SessionSummaryEntity;
import com.neulbom.backend.analysis.SessionSummaryRepository;
import com.neulbom.backend.analysis.api.DailySummaryRequest;
import com.neulbom.backend.analysis.api.QaPair;
import com.neulbom.backend.analysis.api.SessionSummaryRequest;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.diary.api.DiaryFromSessionRequest;
import com.neulbom.backend.diary.api.DiaryResponse;
import com.neulbom.backend.recording.TranscriptEntity;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.session.AnswerEntity;
import com.neulbom.backend.session.AnswerRepository;
import com.neulbom.backend.session.QuestionRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 정서 문답 세션마다 답변과 전사문으로 일기를 만든다. 일일 요약은 리포트에만 사용한다.
 */
@Component
public class DailyDiaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(DailyDiaryGenerator.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final String DIARY_SESSION_TYPE = "emotional_qa";
    private static final String DIARY_TITLE = "오늘의 이야기";

    private final SessionRepository sessionRepository;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final TranscriptRepository transcriptRepository;
    private final AnalysisService analysisService;
    private final DiaryService diaryService;
    private final DiaryRepository diaryRepository;
    private final DailyDiaryWriter dailyDiaryWriter;

    public DailyDiaryGenerator(
            SessionRepository sessionRepository,
            SessionSummaryRepository sessionSummaryRepository,
            AnswerRepository answerRepository,
            QuestionRepository questionRepository,
            TranscriptRepository transcriptRepository,
            AnalysisService analysisService,
            DiaryService diaryService,
            DiaryRepository diaryRepository,
            DailyDiaryWriter dailyDiaryWriter
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.transcriptRepository = transcriptRepository;
        this.analysisService = analysisService;
        this.diaryService = diaryService;
        this.diaryRepository = diaryRepository;
        this.dailyDiaryWriter = dailyDiaryWriter;
    }

    /** 세션 종료 직후 호출하며 같은 세션의 재처리에서는 이미 만든 일기를 반환한다. */
    public DiaryResponse generateForSession(UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !DIARY_SESSION_TYPE.equals(session.getSessionType())
                || !SessionEntity.ENDED.equals(session.getStatus())) return null;
        DiaryEntity existing = diaryRepository.findFirstBySessionIdAndSourceTypeOrderByCreatedAtAsc(sessionId, "session").orElse(null);
        if (existing != null) return diaryService.createFromSession(session.getUserId(),
                new DiaryFromSessionRequest(sessionId, session.getUserId(), null, null, null, null, null));
        List<QaPair> pairs = qaPairs(sessionId);
        if (pairs.isEmpty()) {
            log.info("문답 텍스트가 없어 일기 생성을 건너뜁니다 session_id={}", sessionId);
            return null;
        }
        summarize(session, pairs);
        LocalDate date = (session.getEndedAt() == null ? session.getStartedAt() : session.getEndedAt())
                .atZone(BUSINESS_ZONE).toLocalDate();
        String content = dailyDiaryWriter.write(date, List.of(pairs));
        DiaryResponse diary = diaryService.createFromSession(session.getUserId(),
                new DiaryFromSessionRequest(sessionId, session.getUserId(), null, DIARY_TITLE, content, null, null));
        log.info("세션 일기 생성 user_id={} session_id={} diary_id={}", session.getUserId(), sessionId, diary.diaryId());
        return diary;
    }

    /** 자정 리포트에 쓸 하루 요약을 만들고 아직 빠진 세션 일기는 다시 시도한다. */
    public void summarizeDay(UUID userId, LocalDate date) {
        for (SessionEntity session : emotionalSessionsOn(userId, date)) {
            generateForSession(session.getId());
        }
        analysisService.createDailySummary(new DailySummaryRequest(userId, date, BUSINESS_ZONE.getId()));
    }

    private List<SessionEntity> emotionalSessionsOn(UUID userId, LocalDate date) {
        return sessionRepository.findAllByUserIdOrderByStartedAtDesc(userId).stream()
                .filter(session -> DIARY_SESSION_TYPE.equals(session.getSessionType()))
                .filter(session -> SessionEntity.ENDED.equals(session.getStatus()))
                .filter(session -> date.equals(session.getStartedAt().atZone(BUSINESS_ZONE).toLocalDate()))
                .sorted((a, b) -> a.getStartedAt().compareTo(b.getStartedAt()))
                .toList();
    }

    /** 세션 요약이 있으면 재사용하고, 없으면 답변·전사문으로 Gemini 요약을 만든다. */
    private void summarize(SessionEntity session, List<QaPair> qaPairs) {
        SessionSummaryEntity existing = sessionSummaryRepository.findBySessionId(session.getId()).orElse(null);
        if (existing != null) {
            return;
        }
        analysisService.createSessionSummary(new SessionSummaryRequest(session.getId(), session.getUserId(), qaPairs));
    }

    /** 답변 순서대로 질문·답변 쌍을 만든다. CIST 표본은 제외하고, Gemini 음성 전사가 준비되지 않으면 재시도를 요청한다. */
    List<QaPair> qaPairs(UUID sessionId) {
        return answerRepository.findAllBySessionIdOrderByAnsweredAtAsc(sessionId).stream()
                .map(this::toQaPair)
                .filter(pair -> pair != null)
                .toList();
    }

    private QaPair toQaPair(AnswerEntity answer) {
        var question = questionRepository.findById(answer.getQuestionId())
                .filter(item -> !"cist_bank".equals(item.getQuestionSource()));
        if (question.isEmpty()) {
            return null;
        }
        String text = answerText(answer);
        if (!StringUtils.hasText(text)) {
            if ("gemini".equals(question.get().getQuestionSource())) {
                throw new ExternalServiceUnavailableException("일상 문답 음성 전사가 아직 준비되지 않았습니다.");
            }
            return null;
        }
        return new QaPair(
                question.get().getId(),
                question.get().getContent(),
                text.trim(),
                question.get().getQuestionType());
    }

    /**
     * 텍스트 답변이면 그대로, 음성 답변이면 STT 전사문을 쓴다. 앱의 음성 답변은 answer_text 없이
     * recording_id만 싣고 전사는 recordings 쪽에 따로 저장되므로, answer.transcript_id가 비어 있어도
     * 녹음 ID로 전사를 다시 찾는다.
     */
    private String answerText(AnswerEntity answer) {
        if (StringUtils.hasText(answer.getAnswerText())) {
            return answer.getAnswerText();
        }
        if (answer.getTranscriptId() != null) {
            String byId = transcriptRepository.findById(answer.getTranscriptId())
                    .map(TranscriptEntity::getTranscript).orElse(null);
            if (StringUtils.hasText(byId)) {
                return byId;
            }
        }
        if (answer.getRecordingId() != null) {
            return transcriptRepository.findByRecordingId(answer.getRecordingId())
                    .map(TranscriptEntity::getTranscript).orElse(null);
        }
        return null;
    }
}
