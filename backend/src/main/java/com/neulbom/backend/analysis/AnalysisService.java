package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.guardian.GuardianAccessService;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.recording.TranscriptEntity;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.analysis.api.AcousticAnalysisRequest;
import com.neulbom.backend.analysis.api.AcousticAnalysisResponse;
import com.neulbom.backend.analysis.api.CognitiveAnalysisRequest;
import com.neulbom.backend.analysis.api.CognitiveAnalysisResponse;
import com.neulbom.backend.analysis.api.DailySummariesResponse;
import com.neulbom.backend.analysis.api.DailySummaryRequest;
import com.neulbom.backend.analysis.api.DailySummaryResponse;
import com.neulbom.backend.analysis.api.QaPair;
import com.neulbom.backend.analysis.api.SessionSummaryRequest;
import com.neulbom.backend.analysis.api.SessionSummaryResponse;
import com.neulbom.backend.analysis.api.TranscribeResponse;
import com.neulbom.backend.session.QuestionEntity;
import com.neulbom.backend.session.QuestionRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AnalysisService {

    private static final Set<String> QUESTION_TYPES = Set.of("orientation", "memory", "attention", "language", "emotion");
    private static final Set<String> FUSION_MODES = Set.of("none", "average", "weighted_average");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final RecordingRepository recordingRepository;
    private final TranscriptRepository transcriptRepository;
    private final AcousticAnalysisRepository acousticAnalysisRepository;
    private final CognitiveAnalysisRepository cognitiveAnalysisRepository;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final GuardianAccessService guardianAccessService;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public AnalysisService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            QuestionRepository questionRepository,
            RecordingRepository recordingRepository,
            TranscriptRepository transcriptRepository,
            AcousticAnalysisRepository acousticAnalysisRepository,
            CognitiveAnalysisRepository cognitiveAnalysisRepository,
            SessionSummaryRepository sessionSummaryRepository,
            DailySummaryRepository dailySummaryRepository,
            GuardianAccessService guardianAccessService,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.recordingRepository = recordingRepository;
        this.transcriptRepository = transcriptRepository;
        this.acousticAnalysisRepository = acousticAnalysisRepository;
        this.cognitiveAnalysisRepository = cognitiveAnalysisRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.guardianAccessService = guardianAccessService;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public TranscribeResponse transcribe(
            UUID recordingId,
            UUID userId,
            UUID sessionId,
            UUID questionId,
            MultipartFile ignoredAudioFile
    ) {
        RecordingEntity recording = ownedRecording(userId, recordingId);
        if (!RecordingEntity.ANSWER.equals(recording.getPurpose())
                || !sessionId.equals(recording.getSessionId())
                || !questionId.equals(recording.getQuestionId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "전사 대상 녹음 참조가 올바르지 않습니다.", "recording, session, question을 확인하세요.");
        }
        TranscriptEntity existing = transcriptRepository.findByRecordingId(recordingId).orElse(null);
        if (existing != null) {
            return toTranscribeResponse(existing);
        }

        Instant now = clock.instant();
        // 외부 Whisper adapter 경계. 실제 모델 연결 전에도 상태·계약을 검증할 수 있는 deterministic fallback이다.
        TranscriptEntity transcript = new TranscriptEntity(
                uuidGenerator.generate(),
                recordingId,
                "음성 답변 전사가 완료되었습니다.",
                new BigDecimal("1.000"),
                new BigDecimal("0.80"),
                "ko",
                "whisper-adapter-placeholder",
                "v1",
                "completed",
                now,
                now,
                now);
        transcriptRepository.save(transcript);
        recording.markTranscriptCompleted(now);
        recordingRepository.save(recording);
        return toTranscribeResponse(transcript);
    }

    @Transactional
    public AcousticAnalysisResponse analyzeAcoustic(AcousticAnalysisRequest request) {
        RecordingEntity recording = ownedRecording(request.userId(), request.recordingId());
        if (!request.sessionId().equals(recording.getSessionId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "녹음 세션이 일치하지 않습니다.", "session_id를 확인하세요.");
        }
        String modelVersion = request.modelVersion() == null || request.modelVersion().isBlank()
                ? "v1" : request.modelVersion();
        AcousticAnalysisEntity existing = acousticAnalysisRepository
                .findByRecordingIdAndModelNameAndModelVersion(request.recordingId(), "AST", modelVersion)
                .orElse(null);
        if (existing != null) {
            return toAcousticResponse(existing);
        }
        Instant now = clock.instant();
        AcousticAnalysisEntity analysis = new AcousticAnalysisEntity(
                uuidGenerator.generate(),
                recording.getId(),
                "AST",
                modelVersion,
                new BigDecimal("0.750000"),
                json(Map.of("pause", false, "stability", true)),
                new BigDecimal("3.2000"),
                new BigDecimal("0.150000"),
                new BigDecimal("0.6000"),
                new BigDecimal("0.800000"),
                now,
                now);
        acousticAnalysisRepository.save(analysis);
        return toAcousticResponse(analysis);
    }

    @Transactional
    public CognitiveAnalysisResponse analyzeCognitive(CognitiveAnalysisRequest request) {
        validateQuestionType(request.questionType());
        RecordingEntity recording = transcriptRecording(request.transcriptId(), request.userId(), request.sessionId());
        if (request.acousticAnalysisId() != null
                && !acousticAnalysisRepository.existsById(request.acousticAnalysisId())) {
            throw new ResourceNotFoundException("AST 분석 결과를 찾을 수 없습니다.");
        }
        if (request.questionId() != null) {
            QuestionEntity question = questionRepository.findById(request.questionId())
                    .filter(QuestionEntity::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("질문을 찾을 수 없습니다."));
            if (!question.getQuestionType().equals(request.questionType())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "질문 유형이 일치하지 않습니다.", "question_type을 확인하세요.");
            }
        }
        String fusionMode = request.fusionMode() == null || request.fusionMode().isBlank()
                ? "none" : request.fusionMode();
        if (!FUSION_MODES.contains(fusionMode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "fusion_mode 허용값을 확인하세요.");
        }

        BigDecimal languageScore = heuristicScore(request.transcript());
        BigDecimal acousticScore = request.acousticAnalysisId() == null ? null
                : acousticAnalysisRepository.findById(request.acousticAnalysisId())
                .map(AcousticAnalysisEntity::getAcousticReferenceScore)
                .orElse(null);
        BigDecimal screeningScore = acousticScore == null || "none".equals(fusionMode)
                ? languageScore : combine(languageScore, acousticScore, fusionMode);
        String label = screeningScore.compareTo(new BigDecimal("0.60")) >= 0 ? "normal" : "attention_required";
        String riskLevel = screeningScore.compareTo(new BigDecimal("0.75")) >= 0
                ? "normal" : screeningScore.compareTo(new BigDecimal("0.50")) >= 0 ? "caution" : "warning";
        ObjectNode flags = objectMapper.createObjectNode();
        flags.put("orientation", "orientation".equals(request.questionType()) && screeningScore.compareTo(new BigDecimal("0.60")) < 0);
        flags.put("memory", "memory".equals(request.questionType()) && screeningScore.compareTo(new BigDecimal("0.60")) < 0);
        flags.put("attention", "attention".equals(request.questionType()) && screeningScore.compareTo(new BigDecimal("0.60")) < 0);
        flags.put("language", "language".equals(request.questionType()) && screeningScore.compareTo(new BigDecimal("0.60")) < 0);
        ObjectNode domains = objectMapper.createObjectNode();
        ObjectNode domain = domains.putObject(request.questionType());
        domain.put("correct", screeningScore.compareTo(new BigDecimal("0.60")) >= 0 ? 1 : 0);
        domain.put("total", 1);
        domain.put("score_rate", screeningScore);
        Instant now = clock.instant();
        CognitiveAnalysisEntity analysis = new CognitiveAnalysisEntity(
                uuidGenerator.generate(),
                request.transcriptId(),
                request.acousticAnalysisId(),
                request.userId(),
                request.sessionId(),
                request.questionId(),
                request.questionType(),
                fusionMode,
                "KcELECTRA",
                "v1",
                languageScore,
                screeningScore,
                label,
                riskLevel,
                flags.toString(),
                domains.toString(),
                modelBreakdown(languageScore, acousticScore),
                now,
                now);
        cognitiveAnalysisRepository.save(analysis);
        recording.markAnalysisCompleted(now);
        recordingRepository.save(recording);
        return toCognitiveResponse(analysis);
    }

    @Transactional
    public SessionSummaryResponse createSessionSummary(SessionSummaryRequest request) {
        UserEntity user = activeElder(request.userId());
        SessionEntity session = ownedSession(request.userId(), request.sessionId());
        SessionSummaryEntity existing = sessionSummaryRepository.findBySessionId(session.getId()).orElse(null);
        if (existing != null) {
            return toSessionSummaryResponse(existing);
        }
        String summary = request.qaPairs().stream()
                .map(pair -> pair.question() + " " + pair.answer())
                .reduce((first, second) -> first + " " + second)
                .orElse("대화 내용이 없습니다.");
        List<String> keywords = List.of("대화", "오늘");
        Instant now = clock.instant();
        SessionSummaryEntity entity = new SessionSummaryEntity(
                uuidGenerator.generate(),
                session.getId(),
                user.getId(),
                summary,
                vocabularyScore(request.qaPairs()),
                json(keywords),
                request.qaPairs().size(),
                "completed",
                now,
                now);
        sessionSummaryRepository.save(entity);
        return toSessionSummaryResponse(entity);
    }

    @Transactional(readOnly = true)
    public SessionSummaryResponse getSessionSummary(UUID authenticatedUserId, UUID sessionId) {
        SessionSummaryEntity summary = sessionSummaryRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("세션 요약을 찾을 수 없습니다."));
        authorizeRead(authenticatedUserId, summary.getUserId(), "summary");
        return toSessionSummaryResponse(summary);
    }

    @Transactional
    public DailySummaryResponse createDailySummary(DailySummaryRequest request) {
        activeElder(request.userId());
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? DEFAULT_TIMEZONE : request.timezone();
        if (!DEFAULT_TIMEZONE.equals(timezone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "timezone은 Asia/Seoul만 지원합니다.");
        }
        DailySummaryEntity existing = dailySummaryRepository
                .findByUserIdAndLocalDateAndTimezone(request.userId(), request.localDate(), timezone)
                .orElse(null);
        if (existing != null) {
            return toDailySummaryResponse(existing);
        }
        List<SessionEntity> sessions = sessionRepository.findAllByUserIdOrderByStartedAtDesc(request.userId()).stream()
                .filter(session -> request.localDate().equals(session.getStartedAt().atZone(BUSINESS_ZONE).toLocalDate()))
                .toList();
        int analyzedCount = (int) sessions.stream()
                .flatMap(session -> cognitiveAnalysisRepository.findAllBySessionIdOrderByAnalyzedAtAsc(session.getId()).stream())
                .count();
        Instant now = clock.instant();
        DailySummaryEntity summary = new DailySummaryEntity(
                uuidGenerator.generate(),
                request.userId(),
                request.localDate(),
                timezone,
                sessions.size(),
                analyzedCount,
                "completed",
                sessions.isEmpty() ? "오늘 대화 기록이 없습니다." : "오늘의 대화 분석이 집계되었습니다.",
                json(sessions.stream().map(session -> Map.of("session_id", session.getId().toString())).toList()),
                now,
                now);
        dailySummaryRepository.save(summary);
        return toDailySummaryResponse(summary);
    }

    @Transactional(readOnly = true)
    public DailySummariesResponse getDailySummaries(
            UUID authenticatedUserId,
            UUID requestedUserId,
            LocalDate date,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int limit
    ) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "page는 1 이상, limit은 1~100이어야 합니다.");
        }
        authorizeRead(authenticatedUserId, requestedUserId, "summary");
        List<DailySummaryEntity> summaries = dailySummaryRepository.findAllByUserIdOrderByLocalDateDesc(requestedUserId).stream()
                .filter(summary -> date == null || date.equals(summary.getLocalDate()))
                .filter(summary -> fromDate == null || !summary.getLocalDate().isBefore(fromDate))
                .filter(summary -> toDate == null || !summary.getLocalDate().isAfter(toDate))
                .toList();
        int fromIndex = Math.min((page - 1) * limit, summaries.size());
        int toIndex = Math.min(fromIndex + limit, summaries.size());
        return new DailySummariesResponse(
                summaries.subList(fromIndex, toIndex).stream().map(this::toDailySummaryResponse).toList(),
                summaries.size(),
                page,
                limit);
    }

    private RecordingEntity ownedRecording(UUID userId, UUID recordingId) {
        RecordingEntity recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException("녹음 정보를 찾을 수 없습니다."));
        if (!userId.equals(recording.getUserId())) {
            throw new AccessDeniedException("본인 녹음만 분석할 수 있습니다.");
        }
        return recording;
    }

    private RecordingEntity transcriptRecording(UUID transcriptId, UUID userId, UUID sessionId) {
        TranscriptEntity transcript = transcriptRepository.findById(transcriptId)
                .orElseThrow(() -> new ResourceNotFoundException("전사 결과를 찾을 수 없습니다."));
        RecordingEntity recording = ownedRecording(userId, transcript.getRecordingId());
        if (!sessionId.equals(recording.getSessionId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "전사 세션이 일치하지 않습니다.", "session_id를 확인하세요.");
        }
        return recording;
    }

    private SessionEntity ownedSession(UUID userId, UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("세션을 찾을 수 없습니다."));
        if (!userId.equals(session.getUserId())) {
            throw new AccessDeniedException("본인 세션만 요약할 수 있습니다.");
        }
        return session;
    }

    private UserEntity activeElder(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
        if (!"elder".equals(user.getRole())) {
            throw new AccessDeniedException("고령자 계정만 분석 대상이 될 수 있습니다.");
        }
        return user;
    }

    private void authorizeRead(UUID authenticatedUserId, UUID targetUserId, String scope) {
        if (authenticatedUserId.equals(targetUserId)) {
            activeElder(targetUserId);
            return;
        }
        guardianAccessService.requireAccess(authenticatedUserId, targetUserId, scope);
    }

    private void validateQuestionType(String type) {
        if (!QUESTION_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "question_type 허용값을 확인하세요.");
        }
    }

    private BigDecimal heuristicScore(String transcript) {
        int length = transcript == null ? 0 : transcript.trim().length();
        return new BigDecimal(Math.min(0.95, Math.max(0.25, 0.45 + length / 100.0)))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal combine(BigDecimal language, BigDecimal acoustic, String fusionMode) {
        if ("weighted_average".equals(fusionMode)) {
            return language.multiply(new BigDecimal("0.7")).add(acoustic.multiply(new BigDecimal("0.3")))
                    .setScale(6, RoundingMode.HALF_UP);
        }
        return language.add(acoustic).divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal vocabularyScore(List<QaPair> pairs) {
        long distinct = pairs.stream().flatMap(pair -> java.util.Arrays.stream(pair.answer().split("\\s+"))).distinct().count();
        return new BigDecimal(Math.min(100, distinct * 10)).setScale(2, RoundingMode.HALF_UP);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "분석 결과 저장에 실패했습니다.", "잠시 후 다시 시도하세요.");
        }
    }

    private String modelBreakdown(BigDecimal languageScore, BigDecimal acousticScore) {
        ObjectNode breakdown = objectMapper.createObjectNode();
        breakdown.put("language_model", languageScore);
        if (acousticScore == null) {
            breakdown.putNull("acoustic_model");
        } else {
            breakdown.put("acoustic_model", acousticScore);
        }
        return breakdown.toString();
    }

    private TranscribeResponse toTranscribeResponse(TranscriptEntity transcript) {
        return new TranscribeResponse(
                transcript.getId(),
                transcript.getRecordingId(),
                transcript.getTranscript(),
                transcript.getDurationSec(),
                transcript.getConfidence(),
                transcript.getLanguage(),
                transcript.getModelName());
    }

    private AcousticAnalysisResponse toAcousticResponse(AcousticAnalysisEntity entity) {
        return new AcousticAnalysisResponse(
                entity.getId(), entity.getAcousticReferenceScore(), readJson(entity.getAcousticFlags()),
                entity.getSpeechRate(), entity.getPauseRatio(), entity.getEnergyVariability(),
                entity.getSpeechStability(), entity.getAnalyzedAt());
    }

    private CognitiveAnalysisResponse toCognitiveResponse(CognitiveAnalysisEntity entity) {
        return new CognitiveAnalysisResponse(
                entity.getId(), entity.getLanguageReferenceScore(), entity.getScreeningReferenceScore(),
                entity.getLabel(), entity.getRiskLevel(), readJson(entity.getCognitiveFlags()),
                readJson(entity.getDomainScores()), readJson(entity.getModelBreakdown()), entity.getAnalyzedAt());
    }

    private SessionSummaryResponse toSessionSummaryResponse(SessionSummaryEntity entity) {
        return new SessionSummaryResponse(
                entity.getId(), entity.getSessionId(), entity.getSummary(), entity.getVocabularyScore(),
                readJsonList(entity.getKeywordFlags()), entity.getQaCount(), entity.getSourceStatus(), entity.getCreatedAt());
    }

    private DailySummaryResponse toDailySummaryResponse(DailySummaryEntity entity) {
        return new DailySummaryResponse(
                entity.getId(), entity.getUserId(), entity.getLocalDate(), entity.getTimezone(),
                entity.getSessionCount(), entity.getAnalyzedSessionCount(), entity.getAnalysisStatus(),
                entity.getAnalyzedSessionCount() > 0 ? "대화 분석이 완료되었어요" : "대화 기록을 확인해주세요",
                entity.getSummary(),
                entity.getAnalyzedSessionCount() > 0 ? null : "답변이 쌓이면 더 정확한 추이를 확인할 수 있어요.");
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> readJsonList(String value) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
