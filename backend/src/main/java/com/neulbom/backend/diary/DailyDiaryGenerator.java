package com.neulbom.backend.diary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.neulbom.backend.analysis.AnalysisService;
import com.neulbom.backend.analysis.SessionSummaryEntity;
import com.neulbom.backend.analysis.SessionSummaryRepository;
import com.neulbom.backend.analysis.api.DailySummaryRequest;
import com.neulbom.backend.analysis.api.DailySummaryResponse;
import com.neulbom.backend.analysis.api.QaPair;
import com.neulbom.backend.analysis.api.SessionSummaryRequest;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.diary.api.DiaryFromDailySummaryRequest;
import com.neulbom.backend.diary.api.GenerationStatusResponse;
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
 * 하루치 정서 문답을 분석 요약으로 저장하고, 전체 답변을 통합해 그날의 일기를 만든다.
 *
 * {@link DailyReportScheduler}가 자정 이후 전날 것을 만들 때 쓰는 공통 경로다. 그날의
 * 정서 문답 세션마다 세션 요약(Gemini)이 없으면 답변·STT 전사문으로 만들고, 같은 날 모든
 * 세션의 문답을 Gemini 일기 작성기에 전달해 한 편으로 통합한다. 같은 날 일기가 이미 있으면 {@link DiaryService}가
 * 기존 것을 돌려주므로 재실행해도 안전하다.
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
    private final DailyDiaryWriter dailyDiaryWriter;

    public DailyDiaryGenerator(
            SessionRepository sessionRepository,
            SessionSummaryRepository sessionSummaryRepository,
            AnswerRepository answerRepository,
            QuestionRepository questionRepository,
            TranscriptRepository transcriptRepository,
            AnalysisService analysisService,
            DiaryService diaryService,
            DailyDiaryWriter dailyDiaryWriter
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.transcriptRepository = transcriptRepository;
        this.analysisService = analysisService;
        this.diaryService = diaryService;
        this.dailyDiaryWriter = dailyDiaryWriter;
    }

    /** {@code date}(Asia/Seoul)의 정서 문답으로 그날 일기를 만든다. 결과는 생성 상태 응답. */
    public GenerationStatusResponse generate(UUID userId, LocalDate date) {
        List<List<QaPair>> conversations = new ArrayList<>();
        for (SessionEntity session : emotionalSessionsOn(userId, date)) {
            List<QaPair> pairs = qaPairs(session.getId());
            if (pairs.isEmpty()) {
                log.info("문답 텍스트가 없어 일기 집계에서 건너뜁니다 session_id={}", session.getId());
                continue;
            }
            summarize(session, pairs);
            conversations.add(pairs);
        }
        DailySummaryResponse dailySummary = analysisService.createDailySummary(
                new DailySummaryRequest(userId, date, BUSINESS_ZONE.getId()));
        String content = conversations.isEmpty() ? "" : dailyDiaryWriter.write(date, conversations);
        GenerationStatusResponse status = diaryService.createFromDailySummary(
                userId,
                new DiaryFromDailySummaryRequest(dailySummary.dailySummaryId(), userId, DIARY_TITLE, content, null, null));
        log.info("일기 생성 user_id={} target_date={} sessions={} status={}",
                userId, date, conversations.size(), status.status());
        return status;
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
