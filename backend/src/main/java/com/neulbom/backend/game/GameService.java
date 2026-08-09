package com.neulbom.backend.game;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.diary.DiaryService;
import com.neulbom.backend.game.api.CharacterResponse;
import com.neulbom.backend.game.api.GameHistoryItem;
import com.neulbom.backend.game.api.GameHistoryResponse;
import com.neulbom.backend.game.api.GameResultRequest;
import com.neulbom.backend.game.api.GameResultResponse;
import com.neulbom.backend.game.api.XpAwardRequest;
import com.neulbom.backend.game.api.XpAwardResponse;
import com.neulbom.backend.game.api.XpHistoryItem;
import com.neulbom.backend.game.api.XpHistoryResponse;
import com.neulbom.backend.guardian.GuardianAccessService;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameService {

    private static final Set<String> GAME_TYPES = Set.of("image_match", "consonant", "word_match");
    private static final Set<String> XP_REASONS = Set.of("attendance", "visit", "emotional_qa", "game", "campaign");
    private static final int COMPLETED_GAME_XP = 30;

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final GameResultRepository gameResultRepository;
    private final XpLedgerRepository xpLedgerRepository;
    private final CharacterRepository characterRepository;
    private final GuardianAccessService guardianAccessService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public GameService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            GameResultRepository gameResultRepository,
            XpLedgerRepository xpLedgerRepository,
            CharacterRepository characterRepository,
            GuardianAccessService guardianAccessService,
            UuidGenerator uuidGenerator,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.gameResultRepository = gameResultRepository;
        this.xpLedgerRepository = xpLedgerRepository;
        this.characterRepository = characterRepository;
        this.guardianAccessService = guardianAccessService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GameResultResponse saveResult(UUID authenticatedUserId, GameResultRequest request) {
        requireOwner(authenticatedUserId, request.userId());
        GameResultEntity existing = gameResultRepository.findByClientGameResultId(request.clientGameResultId()).orElse(null);
        if (existing != null) {
            CharacterEntity character = characterRepository.findById(request.userId()).orElse(defaultCharacter(request.userId()));
            int xp = xpLedgerRepository.findByEventId(existing.getId().toString()).map(XpLedgerEntity::getAmount).orElse(0);
            return new GameResultResponse(existing.getId(), existing.getCognitiveIndex(), xp, character.getLevel(), false, true);
        }
        validateResult(request);
        SessionEntity session = sessionRepository.findById(request.sessionId()).orElseThrow(() -> new ResourceNotFoundException("게임 세션을 찾을 수 없습니다."));
        if (!request.userId().equals(session.getUserId())) throw new AccessDeniedException("본인 게임 세션만 저장할 수 있습니다.");
        if (!Set.of("game", "mixed").contains(session.getSessionType())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "게임 세션이 아닙니다.", "session_type을 확인하세요.");
        Instant now = clock.instant();
        BigDecimal cognitiveIndex = BigDecimal.valueOf(request.score()).divide(BigDecimal.valueOf(request.totalQuestions()), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        GameResultEntity result = new GameResultEntity(uuidGenerator.generate(), request.clientGameResultId(), request.userId(), request.sessionId(),
                request.gameType(), request.score(), json(request.responseTimes()), request.errorCount(), request.totalQuestions(), request.matchedPairs(),
                request.attemptCount(), request.durationSec(), request.restartedCount() == null ? 0 : request.restartedCount(), request.completed(), cognitiveIndex, now);
        gameResultRepository.save(result);
        XpAwardResponse xp = request.completed() ? awardXpInternal(request.userId(), COMPLETED_GAME_XP, "game", result.getId().toString())
                : new XpAwardResponse(ensureCharacter(request.userId()).getXpCurrent(), ensureCharacter(request.userId()).getLevel(), false, false);
        return new GameResultResponse(result.getId(), cognitiveIndex, request.completed() ? COMPLETED_GAME_XP : 0, xp.level(), xp.levelUp(), false);
    }

    @Transactional(readOnly = true)
    public GameHistoryResponse history(UUID authenticatedUserId, UUID userId, String gameType, int limit) {
        authorizeRead(authenticatedUserId, userId);
        if (limit < 1 || limit > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "limit은 1~100이어야 합니다.");
        if (gameType != null && !GAME_TYPES.contains(gameType)) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "game_type 허용값을 확인하세요.");
        List<GameResultEntity> results = gameResultRepository.findAllByUserIdOrderByPlayedAtDesc(userId).stream()
                .filter(result -> gameType == null || gameType.equals(result.getGameType())).limit(limit).toList();
        return new GameHistoryResponse(results.stream().map(this::toHistoryItem).toList(), results.size(), limit);
    }

    @Transactional(readOnly = true)
    public CharacterResponse character(UUID authenticatedUserId, UUID userId) {
        authorizeRead(authenticatedUserId, userId);
        return toCharacterResponse(characterRepository.findById(userId).orElse(defaultCharacter(userId)));
    }

    @Transactional(readOnly = true)
    public XpHistoryResponse xpHistory(UUID authenticatedUserId, UUID userId, int limit, String cursor) {
        authorizeRead(authenticatedUserId, userId);
        if (limit < 1 || limit > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "limit은 1~100이어야 합니다.");
        int offset = parseCursor(cursor);
        List<XpLedgerEntity> records = xpLedgerRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        int from = Math.min(offset, records.size());
        int to = Math.min(from + limit, records.size());
        String next = to < records.size() ? Integer.toString(to) : null;
        return new XpHistoryResponse(records.subList(from, to).stream().map(this::toXpHistory).toList(), next);
    }

    @Transactional
    public XpAwardResponse awardXp(UUID userId, XpAwardRequest request) {
        activeElder(userId);
        return awardXpInternal(userId, request.amount(), request.reason(), request.eventId());
    }

    private XpAwardResponse awardXpInternal(UUID userId, int amount, String reason, String eventId) {
        if (amount < 1 || amount > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "amount는 1~500이어야 합니다.");
        if (!XP_REASONS.contains(reason)) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "reason 허용값을 확인하세요.");
        XpLedgerEntity existing = xpLedgerRepository.findByEventId(eventId).orElse(null);
        CharacterEntity character = ensureCharacter(userId);
        if (existing != null) return new XpAwardResponse(character.getXpCurrent(), character.getLevel(), false, true);
        Instant now = clock.instant();
        xpLedgerRepository.save(new XpLedgerEntity(uuidGenerator.generate(), userId, eventId, amount, reason, now));
        int levelDelta = character.awardXp(amount, now);
        characterRepository.save(character);
        return new XpAwardResponse(character.getXpCurrent(), character.getLevel(), levelDelta > 0, false);
    }

    private CharacterEntity ensureCharacter(UUID userId) {
        return characterRepository.findById(userId).orElseGet(() -> characterRepository.save(defaultCharacter(userId)));
    }

    private CharacterEntity defaultCharacter(UUID userId) {
        Instant now = clock.instant();
        return new CharacterEntity(userId, 1, "꼬마 메모이", "egg", 0, 100, null, "[]", now, now);
    }

    private GameHistoryItem toHistoryItem(GameResultEntity result) {
        int xp = xpLedgerRepository.findByEventId(result.getId().toString()).map(XpLedgerEntity::getAmount).orElse(0);
        return new GameHistoryItem(result.getId(), result.getGameType(), result.getScore(), result.getMatchedPairs(), result.getAttemptCount(),
                result.getDurationSec(), result.getRestartedCount(), result.isCompleted(), result.getCognitiveIndex(), xp, result.getPlayedAt());
    }

    private CharacterResponse toCharacterResponse(CharacterEntity character) {
        List<String> unlocked;
        try { unlocked = objectMapper.readValue(character.getUnlocked() == null ? "[]" : character.getUnlocked(), new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { unlocked = List.of(); }
        int stageIndex = switch (character.getStage()) { case "egg" -> 1; case "puppy" -> 2; case "sprout" -> 3; case "flower" -> 4; default -> 5; };
        return new CharacterResponse(character.getUserId(), character.getDisplayName(), character.getLevel(), character.getStage(), stageIndex, 5,
                character.getXpCurrent(), character.getXpGoal(), Math.max(0, character.getXpGoal() - character.getXpCurrent()), character.getSkinId(), unlocked);
    }

    private XpHistoryItem toXpHistory(XpLedgerEntity entry) {
        String title = switch (entry.getReason()) { case "game" -> "기억력 게임 완료"; case "emotional_qa" -> "AI 정서 문답 완료"; case "attendance" -> "연속 출석 보너스"; case "visit" -> "방문 보너스"; default -> "활동 보너스"; };
        return new XpHistoryItem(entry.getId(), entry.getReason(), title, entry.getAmount(), entry.getEventId(), entry.getCreatedAt());
    }

    private void validateResult(GameResultRequest request) {
        if (!GAME_TYPES.contains(request.gameType())) throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "game_type 허용값을 확인하세요.");
        if (request.totalQuestions() < 1 || request.score() < 0 || request.errorCount() < 0 || request.errorCount() > request.totalQuestions()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "게임 결과가 올바르지 않습니다.", "점수·오답·문항 수를 확인하세요.");
        }
        if (request.responseTimes().isEmpty() || request.responseTimes().stream().anyMatch(item -> item == null || item.signum() < 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "응답 시간이 올바르지 않습니다.", "response_times를 확인하세요.");
        }
        if (request.durationSec() < 0 || (request.restartedCount() != null && request.restartedCount() < 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "게임 시간이 올바르지 않습니다.", "duration_sec/restarted_count를 확인하세요.");
        }
        if ("image_match".equals(request.gameType()) && (request.matchedPairs() == null || request.attemptCount() == null || request.matchedPairs() < 0 || request.attemptCount() < 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "기억력 게임 지표가 필요합니다.", "matched_pairs/attempt_count를 확인하세요.");
        }
    }

    private void authorizeRead(UUID authenticatedUserId, UUID userId) {
        if (authenticatedUserId.equals(userId)) { activeElder(userId); return; }
        guardianAccessService.requireAccess(authenticatedUserId, userId, "activity");
    }

    private void requireOwner(UUID authenticatedUserId, UUID userId) { if (!authenticatedUserId.equals(userId)) throw new AccessDeniedException("본인 게임 결과만 저장할 수 있습니다."); activeElder(userId); }
    private void activeElder(UUID userId) { UserEntity user = userRepository.findById(userId).filter(UserEntity::isActive).orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다.")); if (!"elder".equals(user.getRole())) throw new AccessDeniedException("고령자 계정만 사용할 수 있습니다."); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "게임 결과 저장에 실패했습니다.", "잠시 후 다시 시도하세요."); } }
    private int parseCursor(String cursor) { if (cursor == null || cursor.isBlank()) return 0; try { int value = Integer.parseInt(cursor); if (value < 0) throw new NumberFormatException(); return value; } catch (NumberFormatException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "cursor를 확인하세요."); } }
}
