package com.neulbom.backend.session;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.game.GameService;
import com.neulbom.backend.game.XpPolicy;
import com.neulbom.backend.game.api.XpAwardResponse;
import com.neulbom.backend.guardian.GuardianAccessService;
import com.neulbom.backend.session.api.AnswerRequest;
import com.neulbom.backend.session.api.AnswerResponse;
import com.neulbom.backend.session.api.QuestionResponse;
import com.neulbom.backend.session.api.QuestionsResponse;
import com.neulbom.backend.session.api.SessionAnswerItem;
import com.neulbom.backend.session.api.SessionAnswersResponse;
import com.neulbom.backend.session.api.SessionEndResponse;
import com.neulbom.backend.session.api.SessionListItem;
import com.neulbom.backend.session.api.SessionResponse;
import com.neulbom.backend.session.api.SessionSettings;
import com.neulbom.backend.session.api.SessionSettingsUpdateRequest;
import com.neulbom.backend.session.api.SessionStartRequest;
import com.neulbom.backend.session.api.SessionsResponse;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.ConsentEntity;
import com.neulbom.backend.user.ConsentRepository;
import com.neulbom.backend.user.UserPreferenceEntity;
import com.neulbom.backend.user.UserPreferenceRepository;
import com.neulbom.backend.user.UserRepository;
import com.neulbom.backend.user.VoiceProfileEntity;
import com.neulbom.backend.user.VoiceProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final Set<String> SESSION_TYPES = Set.of(
            "cist", "baseline", "onboarding", "emotional_qa", "game", "mixed");
    private static final Set<String> QUESTION_TYPES = Set.of("orientation", "memory", "attention", "language", "emotion");
    private static final Set<String> HEARING_SIDES = Set.of("left", "right", "both", "unknown");
    private static final BigDecimal DEFAULT_SPEECH_RATE = new BigDecimal("0.90");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final VoiceProfileRepository voiceProfileRepository;
    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final GuardianAccessService guardianAccessService;
    private final GameService gameService;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public SessionService(
            UserRepository userRepository,
            ConsentRepository consentRepository,
            UserPreferenceRepository userPreferenceRepository,
            VoiceProfileRepository voiceProfileRepository,
            SessionRepository sessionRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            GuardianAccessService guardianAccessService,
            GameService gameService,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.voiceProfileRepository = voiceProfileRepository;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.guardianAccessService = guardianAccessService;
        this.gameService = gameService;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse startSession(UUID authenticatedUserId, SessionStartRequest request) {
        if (!authenticatedUserId.equals(request.userId())) {
            throw new AccessDeniedException("본인 세션만 시작할 수 있습니다.");
        }
        UserEntity user = activeUser(request.userId());
        if (!"elder".equals(user.getRole())) {
            throw new AccessDeniedException("고령자 계정만 세션을 시작할 수 있습니다.");
        }
        String sessionType = request.sessionType() == null || request.sessionType().isBlank()
                ? "cist" : request.sessionType();
        validateEnum("session_type", sessionType, SESSION_TYPES);
        if (Set.of("baseline", "emotional_qa").contains(sessionType)) {
            requireAgreedConsent(user.getId(), "analysis", "인지 활동 분석 동의가 필요합니다.");
            requireAgreedConsent(user.getId(), "voice_collection", "음성 수집 동의가 필요합니다.");
        }
        SessionSettings settings = settingsForStart(user.getId(), request);
        int totalQuestions = questionCount(sessionType);
        Instant now = clock.instant();
        SessionEntity session = new SessionEntity(
                uuidGenerator.generate(),
                user.getId(),
                sessionType,
                totalQuestions,
                writeSettings(settings),
                Boolean.TRUE.equals(request.offlineMode()),
                now);
        sessionRepository.save(session);
        return toSessionResponse(session, settings);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID authenticatedUserId, UUID sessionId) {
        SessionEntity session = readableSession(authenticatedUserId, sessionId);
        return toSessionResponse(session, readSettings(session.getSettings()));
    }

    @Transactional
    public SessionResponse updateSettings(
            UUID authenticatedUserId,
            UUID sessionId,
            SessionSettingsUpdateRequest request
    ) {
        SessionEntity session = ownedSession(authenticatedUserId, sessionId);
        if (!SessionEntity.ACTIVE.equals(session.getStatus())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "종료된 세션입니다.", "진행 중인 세션만 설정을 변경할 수 있습니다.");
        }
        SessionSettings current = readSettings(session.getSettings());
        String hearingSide = request.preferredHearingSide() == null
                ? current.preferredHearingSide() : request.preferredHearingSide();
        validateEnum("preferred_hearing_side", hearingSide, HEARING_SIDES);
        String voiceProfileId = request.voiceProfileId() == null
                ? current.voiceProfileId() : normalizeOptional(request.voiceProfileId());
        validateVoiceProfile(voiceProfileId);
        BigDecimal speechRate = request.speechRate() == null ? current.speechRate() : request.speechRate();
        validateSpeechRate(speechRate);
        SessionSettings updated = new SessionSettings(
                voiceProfileId,
                hearingSide,
                speechRate,
                valueOrCurrent(request.subtitleEnabled(), current.subtitleEnabled()),
                valueOrCurrent(request.soundEffectEnabled(), current.soundEffectEnabled()));
        session.updateSettings(writeSettings(updated));
        sessionRepository.save(session);
        return toSessionResponse(session, updated);
    }

    @Transactional
    public SessionEndResponse endSession(UUID authenticatedUserId, UUID sessionId) {
        SessionEntity session = ownedSession(authenticatedUserId, sessionId);
        int xpEarned = 0;
        Integer characterLevel = null;
        boolean levelUp = false;
        if (SessionEntity.ACTIVE.equals(session.getStatus())) {
            Instant endedAt = clock.instant();
            session.end(endedAt);
            sessionRepository.save(session);
            if ("baseline".equals(session.getSessionType())) {
                userRepository.findById(session.getUserId())
                        .filter(UserEntity::isActive)
                        .ifPresent(user -> {
                            user.completeBaseline(endedAt);
                            userRepository.save(user);
                        });
            }
            XpAwardResponse award = switch (session.getSessionType()) {
                case "emotional_qa" -> gameService.awardActivityXp(
                        session.getUserId(),
                        XpPolicy.EMOTIONAL_QA_XP,
                        "emotional_qa",
                        "emotional_qa:" + session.getId());
                case "cist", "baseline" -> gameService.awardActivityXp(
                        session.getUserId(),
                        XpPolicy.FIRST_CIST_XP,
                        "cist",
                        "cist:first:" + session.getUserId());
                default -> null;
            };
            if (award != null) {
                xpEarned = award.deduplicated() ? 0 : award.awardedAmount();
                characterLevel = award.level();
                levelUp = award.levelUp();
            }
        }
        return new SessionEndResponse(
                session.getId(),
                session.getStatus(),
                session.getEndedAt(),
                session.getAnsweredCount(),
                "pending",
                "pending",
                null,
                null,
                null,
                null,
                xpEarned,
                characterLevel,
                levelUp);
    }

    @Transactional(readOnly = true)
    public SessionsResponse listSessions(
            UUID requestedUserId,
            UUID authenticatedUserId,
            String sessionType,
            LocalDate date,
            int page,
            int limit
    ) {
        validatePage(page, limit);
        canReadUser(authenticatedUserId, requestedUserId);
        if (sessionType != null) {
            validateEnum("session_type", sessionType, SESSION_TYPES);
        }
        List<SessionEntity> filtered = sessionRepository.findAllByUserIdOrderByStartedAtDesc(requestedUserId)
                .stream()
                .filter(session -> sessionType == null || sessionType.equals(session.getSessionType()))
                .filter(session -> date == null || date.equals(session.getStartedAt().atZone(BUSINESS_ZONE).toLocalDate()))
                .toList();
        int fromIndex = Math.min((page - 1) * limit, filtered.size());
        int toIndex = Math.min(fromIndex + limit, filtered.size());
        List<SessionListItem> sessions = filtered.subList(fromIndex, toIndex).stream()
                .map(session -> new SessionListItem(
                        session.getId(),
                        session.getSessionType(),
                        session.getStatus(),
                        session.getStartedAt(),
                        session.getEndedAt()))
                .toList();
        return new SessionsResponse(sessions, filtered.size(), page, limit);
    }

    @Transactional
    public AnswerResponse saveAnswer(UUID authenticatedUserId, UUID sessionId, AnswerRequest request) {
        SessionEntity session = ownedSession(authenticatedUserId, sessionId);
        if (!SessionEntity.ACTIVE.equals(session.getStatus())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "종료된 세션입니다.", "진행 중인 세션에는 답변을 추가할 수 없습니다.");
        }
        AnswerEntity existing = answerRepository.findBySessionIdAndClientAnswerId(sessionId, request.clientAnswerId())
                .orElse(null);
        if (existing != null) {
            if (!existing.getQuestionId().equals(request.questionId())) {
                throw new ApiException(HttpStatus.CONFLICT, "중복 답변 ID가 다른 문항에 사용되었습니다.", "client_answer_id를 확인하세요.");
            }
            return new AnswerResponse(
                    existing.getId(),
                    existing.getQuestionId(),
                    true,
                    session.getCurrentQuestionOrder(),
                    existing.getSyncStatus());
        }
        if (session.getAnsweredCount() >= session.getTotalQuestions()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "모든 문항에 답변했습니다.", "새 세션을 시작하세요.");
        }
        validateAnswerRequest(request);
        QuestionEntity question = questionRepository.findById(request.questionId())
                .filter(QuestionEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("질문을 찾을 수 없습니다."));
        if (!questionMatchesSession(question, session.getSessionType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "세션 문항이 아닙니다.", "question_id와 session_type을 확인하세요.");
        }
        Instant now = clock.instant();
        AnswerEntity answer = new AnswerEntity(
                uuidGenerator.generate(),
                session.getId(),
                question.getId(),
                request.clientAnswerId(),
                normalizeOptional(request.answerText()),
                request.recordingId(),
                request.transcriptId(),
                request.responseTimeMs(),
                request.answeredAt(),
                now);
        answerRepository.save(answer);
        session.recordAnswer();
        sessionRepository.save(session);
        return new AnswerResponse(
                answer.getId(),
                answer.getQuestionId(),
                true,
                session.getCurrentQuestionOrder(),
                answer.getSyncStatus());
    }

    @Transactional(readOnly = true)
    public SessionAnswersResponse listAnswers(UUID authenticatedUserId, UUID sessionId) {
        SessionEntity session = readableSession(authenticatedUserId, sessionId);
        Map<UUID, QuestionEntity> questions = questionRepository.findAllById(
                        answerRepository.findAllBySessionIdOrderByAnsweredAtAsc(sessionId).stream()
                                .map(AnswerEntity::getQuestionId)
                                .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(QuestionEntity::getId, question -> question));
        List<SessionAnswerItem> answers = answerRepository.findAllBySessionIdOrderByAnsweredAtAsc(sessionId)
                .stream()
                .map(answer -> {
                    QuestionEntity question = questions.get(answer.getQuestionId());
                    return new SessionAnswerItem(
                            answer.getId(),
                            answer.getQuestionId(),
                            question == null ? 0 : question.getDisplayOrder(),
                            question == null ? null : question.getContent(),
                            answer.getAnswerText(),
                            answer.getRecordingId(),
                            answer.getAnsweredAt());
                })
                .toList();
        return new SessionAnswersResponse(session.getId(), session.getSessionType(), answers);
    }

    @Transactional(readOnly = true)
    public QuestionsResponse listDailyQuestions(
            UUID requestedUserId,
            UUID authenticatedUserId,
            String sessionType,
            String questionType
    ) {
        canReadUser(authenticatedUserId, requestedUserId);
        String normalizedSessionType = sessionType == null || sessionType.isBlank() ? "cist" : sessionType;
        validateEnum("session_type", normalizedSessionType, Set.of("cist", "baseline", "emotional_qa"));
        if (questionType != null) {
            validateEnum("type", questionType, QUESTION_TYPES);
        }
        String questionSessionType = questionSessionType(normalizedSessionType);
        List<QuestionEntity> questions = questionType == null
                ? questionRepository.findAllByActiveTrueAndSessionTypeOrderByDisplayOrderAsc(questionSessionType)
                : questionRepository.findAllByActiveTrueAndSessionTypeAndQuestionTypeOrderByDisplayOrderAsc(
                        questionSessionType, questionType);
        return new QuestionsResponse(questions.stream().map(this::toQuestionResponse).toList());
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(UUID questionId) {
        QuestionEntity question = questionRepository.findById(questionId)
                .filter(QuestionEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("질문을 찾을 수 없습니다."));
        return toQuestionResponse(question);
    }

    private SessionEntity readableSession(UUID authenticatedUserId, UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("세션을 찾을 수 없습니다."));
        canReadUser(authenticatedUserId, session.getUserId());
        return session;
    }

    private SessionEntity ownedSession(UUID authenticatedUserId, UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("세션을 찾을 수 없습니다."));
        if (!authenticatedUserId.equals(session.getUserId())) {
            throw new AccessDeniedException("본인 세션만 수정할 수 있습니다.");
        }
        return session;
    }

    private void canReadUser(UUID authenticatedUserId, UUID requestedUserId) {
        if (authenticatedUserId.equals(requestedUserId)) {
            activeUser(requestedUserId);
            return;
        }
        UserEntity requester = activeUser(authenticatedUserId);
        if (!"guardian".equals(requester.getRole())
                || (!hasScope(authenticatedUserId, requestedUserId, "screening")
                && !hasScope(authenticatedUserId, requestedUserId, "summary"))) {
            throw new AccessDeniedException("연결·동의·접근 범위를 확인하세요.");
        }
    }

    private boolean hasScope(UUID guardianId, UUID elderId, String scope) {
        try {
            guardianAccessService.requireAccess(guardianId, elderId, scope);
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    private UserEntity activeUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
    }

    private SessionSettings settingsForStart(UUID userId, SessionStartRequest request) {
        UserPreferenceEntity preference = userPreferenceRepository.findById(userId).orElse(null);
        String voiceProfileId = request.voiceProfileId() != null
                ? normalizeOptional(request.voiceProfileId())
                : preference == null ? null : preference.getVoiceProfileId();
        String hearingSide = request.preferredHearingSide() != null
                ? request.preferredHearingSide()
                : preference == null ? "unknown" : preference.getPreferredHearingSide();
        boolean subtitleEnabled = request.subtitleEnabled() != null
                ? request.subtitleEnabled()
                : preference != null && preference.isSubtitleEnabled();
        BigDecimal speechRate = preference == null ? DEFAULT_SPEECH_RATE : preference.getSpeechRate();
        boolean soundEffectEnabled = preference != null && preference.isSoundEffectEnabled();
        validateEnum("preferred_hearing_side", hearingSide, HEARING_SIDES);
        validateVoiceProfile(voiceProfileId);
        validateSpeechRate(speechRate);
        return new SessionSettings(voiceProfileId, hearingSide, speechRate, subtitleEnabled, soundEffectEnabled);
    }

    private int questionCount(String sessionType) {
        String lookupType = questionSessionType(sessionType);
        int count = questionRepository.findAllByActiveTrueAndSessionTypeOrderByDisplayOrderAsc(lookupType).size();
        return count == 0 ? 1 : count;
    }

    private boolean questionMatchesSession(QuestionEntity question, String sessionType) {
        if ("mixed".equals(sessionType)) {
            return Set.of("cist", "emotional_qa", "game").contains(question.getSessionType());
        }
        return questionSessionType(sessionType).equals(question.getSessionType());
    }

    private String questionSessionType(String sessionType) {
        return switch (sessionType) {
            case "mixed", "baseline", "onboarding" -> "cist";
            default -> sessionType;
        };
    }

    private void validateAnswerRequest(AnswerRequest request) {
        if ((request.answerText() == null || request.answerText().isBlank())
                && request.recordingId() == null && request.transcriptId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "답변 내용이 필요합니다.", "answer_text, recording_id, transcript_id 중 하나를 입력하세요.");
        }
        if (request.answeredAt().isAfter(clock.instant().plusSeconds(60))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "답변 일시가 올바르지 않습니다.", "answered_at은 현재 시각 이후일 수 없습니다.");
        }
    }

    private void validateVoiceProfile(String voiceProfileId) {
        if (voiceProfileId == null) {
            return;
        }
        VoiceProfileEntity voiceProfile = voiceProfileRepository.findById(voiceProfileId)
                .filter(VoiceProfileEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("선택한 안내 음성을 찾을 수 없습니다."));
        if (!"ko".equalsIgnoreCase(voiceProfile.getLanguage())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "선택할 수 없는 안내 음성입니다.", "한국어 안내 음성만 사용할 수 있습니다.");
        }
    }

    private void validateSpeechRate(BigDecimal speechRate) {
        if (speechRate == null || speechRate.compareTo(new BigDecimal("0.75")) < 0
                || speechRate.compareTo(new BigDecimal("1.25")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "speech_rate는 0.75~1.25 범위여야 합니다.");
        }
    }

    private void validateEnum(String field, String value, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", field + " 허용값을 확인하세요.");
        }
    }

    private void validatePage(int page, int limit) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "page는 1 이상, limit은 1~100이어야 합니다.");
        }
    }

    private boolean valueOrCurrent(Boolean value, boolean current) {
        return value == null ? current : value;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void requireAgreedConsent(UUID userId, String consentType, String detail) {
        boolean agreed = consentRepository.findFirstByUserIdAndConsentTypeOrderByCreatedAtDesc(userId, consentType)
                .map(ConsentEntity::isAgreed)
                .orElse(false);
        if (!agreed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "필수 동의가 필요합니다.", detail);
        }
    }

    private String writeSettings(SessionSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "세션 설정 저장에 실패했습니다.", "잠시 후 다시 시도하세요.");
        }
    }

    private SessionSettings readSettings(String settings) {
        if (settings == null || settings.isBlank()) {
            return new SessionSettings(null, "unknown", DEFAULT_SPEECH_RATE, false, false);
        }
        try {
            return objectMapper.readValue(settings, SessionSettings.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "세션 설정 조회에 실패했습니다.", "잠시 후 다시 시도하세요.");
        }
    }

    private SessionResponse toSessionResponse(SessionEntity session, SessionSettings settings) {
        String recordingStatus = session.isOfflineMode() && session.getAnsweredCount() == 0
                ? "device_saved" : "server_uploaded";
        return new SessionResponse(
                session.getId(),
                session.getUserId(),
                session.getSessionType(),
                session.getStatus(),
                session.getCurrentQuestionOrder(),
                session.getAnsweredCount(),
                session.getTotalQuestions(),
                recordingStatus,
                settings,
                session.getStartedAt(),
                session.getEndedAt());
    }

    private QuestionResponse toQuestionResponse(QuestionEntity question) {
        return new QuestionResponse(
                question.getId(),
                question.getContent(),
                question.getQuestionType(),
                question.getDisplayOrder(),
                question.getHint(),
                question.isSubtitleAvailable());
    }
}
