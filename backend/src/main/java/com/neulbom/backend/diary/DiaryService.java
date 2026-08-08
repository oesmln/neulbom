package com.neulbom.backend.diary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.analysis.CognitiveAnalysisEntity;
import com.neulbom.backend.analysis.CognitiveAnalysisRepository;
import com.neulbom.backend.analysis.DailySummaryEntity;
import com.neulbom.backend.analysis.DailySummaryRepository;
import com.neulbom.backend.analysis.SessionSummaryEntity;
import com.neulbom.backend.analysis.SessionSummaryRepository;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.diary.api.CalendarActivitiesResponse;
import com.neulbom.backend.diary.api.CalendarActivity;
import com.neulbom.backend.diary.api.DiariesResponse;
import com.neulbom.backend.diary.api.DiaryCreateRequest;
import com.neulbom.backend.diary.api.DiaryDetailResponse;
import com.neulbom.backend.diary.api.DiaryFromDailySummaryRequest;
import com.neulbom.backend.diary.api.DiaryFromSessionRequest;
import com.neulbom.backend.diary.api.DiaryListItem;
import com.neulbom.backend.diary.api.DiaryResponse;
import com.neulbom.backend.diary.api.DiaryUpdateRequest;
import com.neulbom.backend.diary.api.GenerationStatusResponse;
import com.neulbom.backend.diary.api.ReactionCreateRequest;
import com.neulbom.backend.diary.api.ReactionResponse;
import com.neulbom.backend.diary.api.ReactionsResponse;
import com.neulbom.backend.game.GameResultEntity;
import com.neulbom.backend.game.GameResultRepository;
import com.neulbom.backend.guardian.GuardianAccessService;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {

    public static final String TIMEZONE = "Asia/Seoul";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(TIMEZONE);
    private static final Set<String> SOURCE_TYPES = Set.of("manual", "voice", "session", "daily_summary");
    private static final Set<String> MOODS = Set.of("very_sad", "sad", "neutral", "happy", "very_happy");
    private static final Set<String> REACTION_TYPES = Set.of("heart", "smile", "cheer", "pray", "cry", "message");

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryGenerationJobRepository generationJobRepository;
    private final DiaryReactionRepository reactionRepository;
    private final SessionRepository sessionRepository;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final RecordingRepository recordingRepository;
    private final CognitiveAnalysisRepository cognitiveAnalysisRepository;
    private final GameResultRepository gameResultRepository;
    private final GuardianAccessService guardianAccessService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public DiaryService(
            UserRepository userRepository,
            DiaryRepository diaryRepository,
            DiaryGenerationJobRepository generationJobRepository,
            DiaryReactionRepository reactionRepository,
            SessionRepository sessionRepository,
            SessionSummaryRepository sessionSummaryRepository,
            DailySummaryRepository dailySummaryRepository,
            RecordingRepository recordingRepository,
            CognitiveAnalysisRepository cognitiveAnalysisRepository,
            GameResultRepository gameResultRepository,
            GuardianAccessService guardianAccessService,
            UuidGenerator uuidGenerator,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.diaryRepository = diaryRepository;
        this.generationJobRepository = generationJobRepository;
        this.reactionRepository = reactionRepository;
        this.sessionRepository = sessionRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.recordingRepository = recordingRepository;
        this.cognitiveAnalysisRepository = cognitiveAnalysisRepository;
        this.gameResultRepository = gameResultRepository;
        this.guardianAccessService = guardianAccessService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DiaryResponse create(UUID authenticatedUserId, DiaryCreateRequest request) {
        requireOwner(authenticatedUserId, request.userId());
        validateSource(request.sourceType());
        validateMood(request.mood(), request.moodLevel());
        if ("daily_summary".equals(request.sourceType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "daily_summary 일기는 전용 endpoint를 사용하세요.");
        }
        if (request.sessionId() != null) requireOwnedSession(request.userId(), request.sessionId());
        if (request.recordingId() != null) validateDiaryRecording(request.userId(), request.recordingId());
        if ("voice".equals(request.sourceType()) && request.recordingId() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "음성 일기 원본이 필요합니다.", "recording_id를 확인하세요.");
        }
        return toResponse(diaryRepository.save(new DiaryEntity(uuidGenerator.generate(), request.userId(), request.sourceType(),
                normalizeTitle(request.title()), normalizeContent(request.content()), request.recordingId(), request.sessionId(), null,
                request.mood(), request.moodLevel(), request.writtenAt(), clock.instant(), clock.instant())));
    }

    @Transactional
    public DiaryResponse createFromSession(UUID authenticatedUserId, DiaryFromSessionRequest request) {
        requireOwner(authenticatedUserId, request.userId());
        SessionEntity session = requireOwnedSession(request.userId(), request.sessionId());
        SessionSummaryEntity summary = request.summaryId() == null
                ? sessionSummaryRepository.findBySessionId(session.getId()).orElseThrow(() -> new ResourceNotFoundException("세션 요약을 찾을 수 없습니다."))
                : sessionSummaryRepository.findById(request.summaryId()).filter(item -> item.getSessionId().equals(session.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("세션 요약을 찾을 수 없습니다."));
        validateMood(request.mood(), request.moodLevel());
        Instant now = clock.instant();
        return toResponse(diaryRepository.save(new DiaryEntity(uuidGenerator.generate(), request.userId(), "session",
                normalizeTitle(request.title() == null ? "오늘의 이야기" : request.title()), normalizeContent(request.content() == null ? summary.getSummary() : request.content()),
                null, session.getId(), null, request.mood(), request.moodLevel(), now, now, now)));
    }

    @Transactional
    public GenerationStatusResponse createFromDailySummary(UUID authenticatedUserId, DiaryFromDailySummaryRequest request) {
        requireOwner(authenticatedUserId, request.userId());
        DailySummaryEntity summary = dailySummaryRepository.findById(request.dailySummaryId())
                .filter(item -> item.getUserId().equals(request.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("일일 요약을 찾을 수 없습니다."));
        GenerationStatusResponse existing = generationJobRepository.findByDailySummaryId(summary.getId()).map(this::toGenerationStatus).orElse(null);
        if (existing != null) return existing;
        DiaryEntity existingDiary = diaryRepository.findByDailySummaryId(summary.getId()).orElse(null);
        if (existingDiary != null) return completedStatus(existingDiary, summary.getLocalDate());
        validateMood(request.mood(), request.moodLevel());
        Instant now = clock.instant();
        String status;
        UUID diaryId = null;
        Instant availableAt = null;
        String failureReason = null;
        if (summary.getSessionCount() <= 0) {
            status = "conversation_incomplete";
            failureReason = "insufficient_conversation";
        } else if (!"completed".equals(summary.getAnalysisStatus())) {
            status = "processing";
        } else {
            DiaryEntity diary = diaryRepository.save(new DiaryEntity(uuidGenerator.generate(), request.userId(), "daily_summary",
                    normalizeTitle(request.title() == null ? "오늘의 대화" : request.title()),
                    normalizeContent(request.content() == null ? summary.getSummary() : request.content()), null, null, summary.getId(),
                    request.mood(), request.moodLevel(), summary.getLocalDate().atStartOfDay(BUSINESS_ZONE).toInstant(), now, now));
            diaryId = diary.getId();
            availableAt = now;
            status = "completed";
        }
        DiaryGenerationJobEntity job = new DiaryGenerationJobEntity(uuidGenerator.generate(), request.userId(), summary.getId(), summary.getLocalDate(),
                status, now, availableAt, diaryId, failureReason, 0, 3, null, now, now);
        generationJobRepository.save(job);
        return toGenerationStatus(job);
    }

    @Transactional(readOnly = true)
    public GenerationStatusResponse generationStatus(UUID authenticatedUserId, UUID userId, LocalDate targetDate) {
        authorizeRead(authenticatedUserId, userId, "diary");
        DiaryGenerationJobEntity job = generationJobRepository.findByUserIdAndTargetDate(userId, targetDate).orElse(null);
        if (job != null) return toGenerationStatus(job);
        Instant scheduledAt = targetDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return new GenerationStatusResponse(null, targetDate, "scheduled", scheduledAt, null, null, null, true,
                "내일 일기 생성 예정", "오늘 대화를 바탕으로 내일 일기를 준비해요.");
    }

    @Transactional(readOnly = true)
    public DiariesResponse list(UUID authenticatedUserId, UUID userId, LocalDate date, LocalDate fromDate, LocalDate toDate, int page, int limit) {
        authorizeRead(authenticatedUserId, userId, "diary");
        if (page < 1 || limit < 1 || limit > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "page/limit을 확인하세요.");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "날짜 범위를 확인하세요.");
        List<DiaryEntity> diaries = diaryRepository.findAllByUserIdOrderByWrittenAtDesc(userId).stream()
                .filter(diary -> date == null || date.equals(localDate(diary.getWrittenAt())))
                .filter(diary -> fromDate == null || !localDate(diary.getWrittenAt()).isBefore(fromDate))
                .filter(diary -> toDate == null || !localDate(diary.getWrittenAt()).isAfter(toDate)).toList();
        int from = Math.min((page - 1) * limit, diaries.size());
        int to = Math.min(from + limit, diaries.size());
        return new DiariesResponse(diaries.subList(from, to).stream().map(this::toListItem).toList(), diaries.size(), page, limit);
    }

    @Transactional(readOnly = true)
    public Object getByIdOrUser(UUID authenticatedUserId, UUID id, LocalDate date, LocalDate fromDate, LocalDate toDate, int page, int limit) {
        if (diaryRepository.existsById(id)) return get(authenticatedUserId, id);
        return list(authenticatedUserId, id, date, fromDate, toDate, page, limit);
    }

    @Transactional(readOnly = true)
    public DiaryDetailResponse get(UUID authenticatedUserId, UUID diaryId) {
        DiaryEntity diary = requireDiary(diaryId);
        authorizeRead(authenticatedUserId, diary.getUserId(), "diary");
        return new DiaryDetailResponse(diary.getId(), diary.getUserId(), diary.getSourceType(), diary.getTitle(), diary.getContent(),
                diary.getSessionId(), diary.getDailySummaryId(), diary.getMood(), diary.getMoodLevel(), diary.getWrittenAt(), diary.getCreatedAt(), diary.getUpdatedAt(),
                reactionResponses(diary.getId()));
    }

    @Transactional
    public DiaryResponse update(UUID authenticatedUserId, UUID diaryId, DiaryUpdateRequest request) {
        DiaryEntity diary = requireDiary(diaryId);
        requireOwner(authenticatedUserId, diary.getUserId());
        if (request.title() == null && request.content() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "수정할 값이 없습니다.", "title 또는 content를 보내세요.");
        if (request.content() != null) normalizeContent(request.content());
        diary.update(request.title() == null ? null : normalizeTitle(request.title()), request.content() == null ? null : normalizeContent(request.content()), clock.instant());
        return toResponse(diary);
    }

    @Transactional
    public void delete(UUID authenticatedUserId, UUID diaryId) {
        DiaryEntity diary = requireDiary(diaryId);
        requireOwner(authenticatedUserId, diary.getUserId());
        diaryRepository.delete(diary);
    }

    @Transactional
    public ReactionResponse createReaction(UUID authenticatedUserId, UUID diaryId, ReactionCreateRequest request) {
        DiaryEntity diary = requireDiary(diaryId);
        requireGuardianAccess(authenticatedUserId, diary.getUserId(), "diary");
        String type = request.reactionType() == null ? "" : request.reactionType().trim();
        if (!REACTION_TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "reaction_type 허용값을 확인하세요.");
        String message = request.message() == null ? null : request.message().trim();
        if ("message".equals(type) && (message == null || message.isBlank())) throw new ApiException(HttpStatus.BAD_REQUEST, "메시지가 필요합니다.", "message 반응은 본문이 필요합니다.");
        String normalizedMessage = "message".equals(type) ? message : null;
        DiaryReactionEntity reaction = reactionRepository.findByDiaryIdAndReactorIdAndReactionType(diaryId, authenticatedUserId, type)
                .orElseGet(() -> reactionRepository.save(new DiaryReactionEntity(uuidGenerator.generate(), diaryId, authenticatedUserId, type, normalizedMessage, clock.instant())));
        return toReactionResponse(reaction);
    }

    @Transactional(readOnly = true)
    public ReactionsResponse reactions(UUID authenticatedUserId, UUID diaryId) {
        DiaryEntity diary = requireDiary(diaryId);
        authorizeRead(authenticatedUserId, diary.getUserId(), "diary");
        return new ReactionsResponse(reactionResponses(diaryId));
    }

    @Transactional(readOnly = true)
    public CalendarActivitiesResponse calendar(UUID authenticatedUserId, UUID userId, LocalDate fromDate, LocalDate toDate, String types) {
        authorizeRead(authenticatedUserId, userId, "activity");
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "from_date/to_date를 확인하세요.");
        Set<String> filters = types == null || types.isBlank() ? Set.of("diary", "screening", "emotional_qa", "game", "campaign")
                : java.util.Arrays.stream(types.split(",")).map(String::trim).filter(item -> !item.isBlank()).collect(Collectors.toSet());
        List<CalendarActivity> activities = new ArrayList<>();
        if (filters.contains("diary")) diaryRepository.findAllByUserIdOrderByWrittenAtDesc(userId).stream().filter(diary -> inRange(diary.getWrittenAt(), fromDate, toDate))
                .forEach(diary -> activities.add(new CalendarActivity(diary.getId(), "diary", diary.getId(), localDate(diary.getWrittenAt()),
                        diary.getTitle() == null ? "일기" : diary.getTitle(), "completed", diaryMetadata(diary))));
        List<SessionEntity> sessions = sessionRepository.findAllByUserIdOrderByStartedAtDesc(userId);
        if (filters.contains("emotional_qa")) sessions.stream().filter(session -> "emotional_qa".equals(session.getSessionType())).filter(session -> inRange(session.getStartedAt(), fromDate, toDate))
                .forEach(session -> activities.add(new CalendarActivity(session.getId(), "emotional_qa", session.getId(), localDate(session.getStartedAt()), "AI 정서 문답", "completed", objectMapper.createObjectNode())));
        if (filters.contains("screening")) cognitiveAnalysisRepository.findAllByUserIdOrderByAnalyzedAtDesc(userId).stream().filter(analysis -> inRange(analysis.getAnalyzedAt(), fromDate, toDate))
                .forEach(analysis -> activities.add(new CalendarActivity(analysis.getId(), "screening", analysis.getSessionId(), localDate(analysis.getAnalyzedAt()), "인지 활동 결과", "completed", objectMapper.createObjectNode())));
        if (filters.contains("game")) gameResultRepository.findAllByUserIdAndPlayedAtBetweenOrderByPlayedAtDesc(userId,
                fromDate.atStartOfDay(BUSINESS_ZONE).toInstant(), toDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()).stream()
                .forEach(game -> activities.add(new CalendarActivity(game.getId(), "game", game.getId(), localDate(game.getPlayedAt()), "기억력 게임", game.isCompleted() ? "completed" : "in_progress", objectMapper.createObjectNode())));
        activities.sort(Comparator.comparing(CalendarActivity::activityDate).reversed());
        return new CalendarActivitiesResponse(activities);
    }

    private DiaryEntity requireDiary(UUID diaryId) { return diaryRepository.findById(diaryId).orElseThrow(() -> new ResourceNotFoundException("일기를 찾을 수 없습니다.")); }
    private SessionEntity requireOwnedSession(UUID userId, UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("세션을 찾을 수 없습니다."));
        if (!userId.equals(session.getUserId())) throw new AccessDeniedException("본인 세션만 일기로 만들 수 있습니다.");
        return session;
    }
    private void validateDiaryRecording(UUID userId, UUID recordingId) {
        RecordingEntity recording = recordingRepository.findById(recordingId).orElseThrow(() -> new ResourceNotFoundException("녹음을 찾을 수 없습니다."));
        if (!userId.equals(recording.getUserId())) throw new AccessDeniedException("본인 녹음만 일기로 사용할 수 있습니다.");
        if (!RecordingEntity.DIARY.equals(recording.getPurpose())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "일기용 녹음이 아닙니다.", "purpose=diary 녹음을 사용하세요.");
    }
    private void validateSource(String sourceType) { if (!SOURCE_TYPES.contains(sourceType)) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "source_type 허용값을 확인하세요."); }
    private void validateMood(String mood, Integer level) {
        if (mood != null && !MOODS.contains(mood)) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "mood 허용값을 확인하세요.");
        if (level != null && (level < 1 || level > 5)) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "mood_level은 1~5여야 합니다.");
    }
    private String normalizeTitle(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizeContent(String value) { if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "일기 본문이 필요합니다.", "content를 확인하세요."); return value.trim(); }
    private void requireOwner(UUID authenticatedUserId, UUID userId) { if (!authenticatedUserId.equals(userId)) throw new AccessDeniedException("본인 일기만 작성할 수 있습니다."); activeElder(userId); }
    private void activeElder(UUID userId) { UserEntity user = userRepository.findById(userId).filter(UserEntity::isActive).orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다.")); if (!"elder".equals(user.getRole())) throw new AccessDeniedException("고령자 계정만 일기를 사용할 수 있습니다."); }
    private void authorizeRead(UUID authenticatedUserId, UUID userId, String scope) { if (authenticatedUserId.equals(userId)) { activeElder(userId); return; } requireGuardianAccess(authenticatedUserId, userId, scope); }
    private void requireGuardianAccess(UUID guardianId, UUID elderId, String scope) { UserEntity guardian = userRepository.findById(guardianId).filter(UserEntity::isActive).orElseThrow(() -> new ResourceNotFoundException("보호자 정보를 찾을 수 없습니다.")); if (!"guardian".equals(guardian.getRole())) throw new AccessDeniedException("보호자 계정만 사용할 수 있습니다."); guardianAccessService.requireAccess(guardianId, elderId, scope); }
    private DiaryResponse toResponse(DiaryEntity diary) { return new DiaryResponse(diary.getId(), diary.getUserId(), diary.getSourceType(), diary.getTitle(), diary.getContent(), diary.getRecordingId(), diary.getSessionId(), diary.getDailySummaryId(), diary.getMood(), diary.getMoodLevel(), diary.getWrittenAt(), diary.getCreatedAt(), diary.getUpdatedAt()); }
    private DiaryListItem toListItem(DiaryEntity diary) { String preview = diary.getContent().length() > 80 ? diary.getContent().substring(0, 80) : diary.getContent(); return new DiaryListItem(diary.getId(), diary.getTitle(), preview, diary.getSourceType(), diary.getMood(), diary.getMoodLevel(), diary.getWrittenAt(), reactionRepository.findAllByDiaryIdOrderByCreatedAtAsc(diary.getId()).size()); }
    private List<ReactionResponse> reactionResponses(UUID diaryId) { return reactionRepository.findAllByDiaryIdOrderByCreatedAtAsc(diaryId).stream().map(this::toReactionResponse).toList(); }
    private ReactionResponse toReactionResponse(DiaryReactionEntity reaction) { String name = userRepository.findById(reaction.getReactorId()).map(UserEntity::getName).orElse("사용자"); return new ReactionResponse(reaction.getId(), reaction.getDiaryId(), reaction.getReactorId(), name, reaction.getReactionType(), reaction.getMessage(), reaction.getCreatedAt()); }
    private GenerationStatusResponse toGenerationStatus(DiaryGenerationJobEntity job) { boolean retryable = Set.of("processing", "failed").contains(job.getStatus()) && job.getRetryCount() < job.getMaxRetries(); String label = switch (job.getStatus()) { case "completed" -> "일기 생성 완료"; case "processing" -> "일기 생성 중"; case "failed" -> "일기 생성 실패"; case "conversation_incomplete" -> "대화가 부족해요"; default -> "일기 생성 예정"; }; String message = switch (job.getStatus()) { case "completed" -> "오늘의 일기를 확인해 보세요."; case "processing" -> "오늘 대화를 일기로 정리하고 있어요."; case "failed" -> "잠시 후 다시 시도해 주세요."; case "conversation_incomplete" -> "대화가 쌓이면 일기를 만들 수 있어요."; default -> "내일 일기를 준비해요."; }; return new GenerationStatusResponse(job.getId(), job.getTargetDate(), job.getStatus(), job.getScheduledAt(), job.getAvailableAt(), job.getDiaryId(), job.getFailureReason(), retryable, label, message); }
    private GenerationStatusResponse completedStatus(DiaryEntity diary, LocalDate targetDate) { return new GenerationStatusResponse(null, targetDate, "completed", null, diary.getCreatedAt(), diary.getId(), null, false, "일기 생성 완료", "오늘의 일기를 확인해 보세요."); }
    private LocalDate localDate(Instant instant) { return instant.atZone(BUSINESS_ZONE).toLocalDate(); }
    private boolean inRange(Instant instant, LocalDate from, LocalDate to) { LocalDate date = localDate(instant); return !date.isBefore(from) && !date.isAfter(to); }
    private JsonNode diaryMetadata(DiaryEntity diary) { var node = objectMapper.createObjectNode(); if (diary.getMood() != null) node.put("mood", diary.getMood()); if (diary.getMoodLevel() != null) node.put("mood_level", diary.getMoodLevel()); return node; }
}
