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
 * 하루치 정서 문답을 Gemini 요약으로 묶어 그날의 일기를 만든다.
 *
 * {@link DailyReportScheduler}가 자정 이후 전날 것을 만들 때 쓰는 공통 경로다. 그날의
 * 정서 문답 세션마다 세션 요약(Gemini)이 없으면 답변·STT 전사문으로 만들고, 요약문을 이어
 * 붙여 일기 본문으로 넘긴다. 문답이 없던 날은 기존과 같이 일일 요약만 만들어
 * {@code conversation_incomplete}로 남긴다. 같은 날 일기가 이미 있으면 {@link DiaryService}가
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

    public DailyDiaryGenerator(
            SessionRepository sessionRepository,
            SessionSummaryRepository sessionSummaryRepository,
            AnswerRepository answerRepository,
            QuestionRepository questionRepository,
            TranscriptRepository transcriptRepository,
            AnalysisService analysisService,
            DiaryService diaryService
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.transcriptRepository = transcriptRepository;
        this.analysisService = analysisService;
        this.diaryService = diaryService;
    }

    /** {@code date}(Asia/Seoul)의 정서 문답으로 그날 일기를 만든다. 결과는 생성 상태 응답. */
    public GenerationStatusResponse generate(UUID userId, LocalDate date) {
        List<String> summaries = new ArrayList<>();
        for (SessionEntity session : emotionalSessionsOn(userId, date)) {
            String summary = summarize(session);
            if (StringUtils.hasText(summary)) {
                summaries.add(summary.trim());
            }
        }
        DailySummaryResponse dailySummary = analysisService.createDailySummary(
                new DailySummaryRequest(userId, date, BUSINESS_ZONE.getId()));
        String content = summaries.isEmpty() ? null : String.join("\n\n", summaries);
        GenerationStatusResponse status = diaryService.createFromDailySummary(
                userId,
                new DiaryFromDailySummaryRequest(dailySummary.dailySummaryId(), userId, DIARY_TITLE, content, null, null));
        log.info("일기 생성 user_id={} target_date={} sessions={} status={}",
                userId, date, summaries.size(), status.status());
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
    private String summarize(SessionEntity session) {
        SessionSummaryEntity existing = sessionSummaryRepository.findBySessionId(session.getId()).orElse(null);
        if (existing != null) {
            return existing.getSummary();
        }
        List<QaPair> qaPairs = qaPairs(session.getId());
        if (qaPairs.isEmpty()) {
            log.info("문답 텍스트가 없어 요약을 건너뜁니다 session_id={}", session.getId());
            return null;
        }
        return analysisService.createSessionSummary(
                new SessionSummaryRequest(session.getId(), session.getUserId(), qaPairs)).summary();
    }

    /** 답변 순서대로 (질문 본문, 답변 텍스트) 쌍을 만든다. 텍스트를 못 찾는 답변은 건너뛴다. */
    List<QaPair> qaPairs(UUID sessionId) {
        return answerRepository.findAllBySessionIdOrderByAnsweredAtAsc(sessionId).stream()
                .map(this::toQaPair)
                .filter(pair -> pair != null)
                .toList();
    }

    private QaPair toQaPair(AnswerEntity answer) {
        String text = answerText(answer);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return questionRepository.findById(answer.getQuestionId())
                .map(question -> new QaPair(
                        question.getId(),
                        question.getContent(),
                        text.trim(),
                        question.getQuestionType()))
                .orElse(null);
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
