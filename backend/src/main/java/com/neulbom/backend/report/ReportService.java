package com.neulbom.backend.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.analysis.CognitiveAnalysisEntity;
import com.neulbom.backend.analysis.CognitiveAnalysisRepository;
import com.neulbom.backend.analysis.DailySummaryEntity;
import com.neulbom.backend.analysis.DailySummaryRepository;
import com.neulbom.backend.analysis.ScreeningResultEntity;
import com.neulbom.backend.analysis.ScreeningResultRepository;
import com.neulbom.backend.analysis.SessionSummaryEntity;
import com.neulbom.backend.analysis.SessionSummaryRepository;
import com.neulbom.backend.common.audit.AuditLogEntity;
import com.neulbom.backend.common.audit.AuditLogRepository;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.diary.DiaryEntity;
import com.neulbom.backend.diary.DiaryGenerationJobEntity;
import com.neulbom.backend.diary.DiaryGenerationJobRepository;
import com.neulbom.backend.diary.DiaryRepository;
import com.neulbom.backend.game.CharacterEntity;
import com.neulbom.backend.game.CharacterRepository;
import com.neulbom.backend.game.GameResultEntity;
import com.neulbom.backend.game.GameResultRepository;
import com.neulbom.backend.guardian.GuardianAccessService;
import com.neulbom.backend.guardian.ReportExportEntity;
import com.neulbom.backend.guardian.ReportExportRepository;
import com.neulbom.backend.notification.NotificationEntity;
import com.neulbom.backend.notification.NotificationRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import com.neulbom.backend.report.api.BenchmarkResponse;
import com.neulbom.backend.report.api.DashboardResponse;
import com.neulbom.backend.report.api.GuardianReportResponse;
import com.neulbom.backend.report.api.HistoryRecordResponse;
import com.neulbom.backend.report.api.HistoryResponse;
import com.neulbom.backend.report.api.ReportExportResponse;
import com.neulbom.backend.report.api.ScreeningResultResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    public static final String TIMEZONE = "Asia/Seoul";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(TIMEZONE);
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int SCORE_MAX = 30;
    private static final int MIN_REGIONAL_SAMPLE = 5;

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final CognitiveAnalysisRepository cognitiveAnalysisRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryGenerationJobRepository diaryGenerationJobRepository;
    private final CharacterRepository characterRepository;
    private final GameResultRepository gameResultRepository;
    private final NotificationRepository notificationRepository;
    private final GuardianAccessService guardianAccessService;
    private final ReportExportRepository reportExportRepository;
    private final AuditLogRepository auditLogRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ReportService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            CognitiveAnalysisRepository cognitiveAnalysisRepository,
            ScreeningResultRepository screeningResultRepository,
            SessionSummaryRepository sessionSummaryRepository,
            DailySummaryRepository dailySummaryRepository,
            DiaryRepository diaryRepository,
            DiaryGenerationJobRepository diaryGenerationJobRepository,
            CharacterRepository characterRepository,
            GameResultRepository gameResultRepository,
            NotificationRepository notificationRepository,
            GuardianAccessService guardianAccessService,
            ReportExportRepository reportExportRepository,
            AuditLogRepository auditLogRepository,
            UuidGenerator uuidGenerator,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.cognitiveAnalysisRepository = cognitiveAnalysisRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.diaryRepository = diaryRepository;
        this.diaryGenerationJobRepository = diaryGenerationJobRepository;
        this.characterRepository = characterRepository;
        this.gameResultRepository = gameResultRepository;
        this.notificationRepository = notificationRepository;
        this.guardianAccessService = guardianAccessService;
        this.reportExportRepository = reportExportRepository;
        this.auditLogRepository = auditLogRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ScreeningResultResponse getScreeningResult(UUID authenticatedUserId, UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("검사 세션을 찾을 수 없습니다."));
        boolean guardian = authorizeRead(authenticatedUserId, session.getUserId(), "screening");
        ScreeningResultEntity result = screeningResultRepository.findBySessionId(sessionId).orElse(null);
        CognitiveAnalysisEntity analysis = latestForSession(sessionId);
        SessionSummaryEntity summary = sessionSummaryRepository.findBySessionId(sessionId).orElse(null);
        return toScreeningResponse(session, result, analysis, summary == null ? null : summary.getId(), guardian);
    }

    @Transactional(readOnly = true)
    public HistoryResponse getHistory(
            UUID authenticatedUserId,
            UUID userId,
            int limit,
            LocalDate fromDate,
            LocalDate toDate,
            String aggregation
    ) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "limit은 1~100이어야 합니다.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "from_date는 to_date보다 늦을 수 없습니다.");
        }
        boolean guardian = authorizeRead(authenticatedUserId, userId, "screening");
        String normalizedAggregation = aggregation == null || aggregation.isBlank() ? "session" : aggregation;
        if (!List.of("answer", "session", "day", "user").contains(normalizedAggregation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "aggregation 허용값을 확인하세요.");
        }
        List<CognitiveAnalysisEntity> analyses = cognitiveAnalysisRepository.findAllByUserIdOrderByAnalyzedAtDesc(userId).stream()
                .filter(analysis -> inDateRange(analysis.getAnalyzedAt(), fromDate, toDate))
                .toList();
        List<CognitiveAnalysisEntity> grouped = groupAnalyses(analyses, normalizedAggregation);
        List<CognitiveAnalysisEntity> limited = grouped.stream().limit(limit).toList();
        BigDecimal average30d = averageScores(analyses.stream()
                .filter(analysis -> !analysis.getAnalyzedAt().isBefore(clock.instant().minus(Duration.ofDays(30))))
                .toList());
        List<HistoryRecordResponse> records = new ArrayList<>();
        for (int index = 0; index < limited.size(); index++) {
            CognitiveAnalysisEntity current = limited.get(index);
            CognitiveAnalysisEntity previous = index + 1 < grouped.size() ? grouped.get(index + 1) : null;
            records.add(toHistoryRecord(current, previous, average30d, guardian));
        }
        return new HistoryResponse(records, grouped.size(), normalizedAggregation, analyses.size() >= 2);
    }

    @Transactional(readOnly = true)
    public BenchmarkResponse getBenchmark(
            UUID authenticatedUserId,
            UUID userId,
            String provinceCode,
            String districtCode,
            LocalDate fromDate,
            LocalDate toDate,
            String aggregation
    ) {
        if (provinceCode == null || provinceCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "province_code가 필요합니다.");
        }
        requireGuardianAccess(authenticatedUserId, userId, "screening");
        String normalizedAggregation = aggregation == null || aggregation.isBlank() ? "month" : aggregation;
        if (!List.of("month", "quarter").contains(normalizedAggregation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "aggregation 허용값을 확인하세요.");
        }
        List<CognitiveAnalysisEntity> analyses = cognitiveAnalysisRepository.findAllByUserIdOrderByAnalyzedAtDesc(userId).stream()
                .filter(analysis -> inDateRange(analysis.getAnalyzedAt(), fromDate, toDate))
                .toList();
        Map<String, List<CognitiveAnalysisEntity>> byPeriod = analyses.stream().collect(Collectors.groupingBy(
                analysis -> period(analysis.getAnalyzedAt(), normalizedAggregation), LinkedHashMap::new, Collectors.toList()));
        List<BenchmarkResponse.UserPoint> userSeries = byPeriod.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new BenchmarkResponse.UserPoint(entry.getKey(), displayScore(averageScores(entry.getValue())), rate(averageScores(entry.getValue()))))
                .toList();
        String regionCode = districtCode == null || districtCode.isBlank() ? provinceCode : provinceCode + "-" + districtCode;
        BenchmarkResponse.RegionalPoint suppressed = new BenchmarkResponse.RegionalPoint(
                regionCode,
                "지역 기준선 준비 중",
                userSeries.isEmpty() ? YearMonth.now(BUSINESS_ZONE).format(PERIOD_FORMAT) : userSeries.get(userSeries.size() - 1).period(),
                null,
                0,
                true);
        return new BenchmarkResponse(userSeries, List.of(suppressed), "neulbom-regional-benchmark", clock.instant());
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID authenticatedUserId, UUID userId) {
        UserEntity target = activeUser(userId);
        if (!authenticatedUserId.equals(userId)) {
            guardianAccessService.requireAccess(authenticatedUserId, userId, "summary");
        }
        List<SessionEntity> sessions = sessionRepository.findAllByUserIdOrderByStartedAtDesc(userId).stream()
                .filter(session -> !Set.of("baseline", "onboarding").contains(session.getSessionType()))
                .toList();
        boolean guardian = !authenticatedUserId.equals(userId);
        DashboardResponse.ScreeningSummary latestScreening = sessions.stream()
                .map(session -> latestScreeningSummary(session.getId(), guardian))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        SessionSummaryEntity latestSummaryEntity = sessionSummaryRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream().findFirst().orElse(null);
        DashboardResponse.Summary latestSummary = latestSummaryEntity == null ? null
                : new DashboardResponse.Summary(latestSummaryEntity.getId(), latestSummaryEntity.getSessionId(), latestSummaryEntity.getSummary(), latestSummaryEntity.getCreatedAt());
        Instant now = clock.instant();
        LocalDate today = now.atZone(BUSINESS_ZONE).toLocalDate();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant monthEnd = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        long emotionalCount = sessions.stream().filter(session -> "emotional_qa".equals(session.getSessionType()))
                .filter(session -> SessionEntity.ENDED.equals(session.getStatus()))
                .filter(session -> between(session.getStartedAt(), monthStart, monthEnd)).count();
        long gameCount = gameResultRepository.countByUserIdAndPlayedAtBetweenAndCompletedTrue(userId, monthStart, monthEnd);
        int attendanceDays = (int) sessions.stream().filter(session -> between(session.getStartedAt(), monthStart, monthEnd))
                .map(session -> session.getStartedAt().atZone(BUSINESS_ZONE).toLocalDate()).distinct().count();
        int streak = attendanceStreak(sessions, today);
        CharacterEntity character = characterRepository.findById(userId).orElse(null);
        String characterDisplayName = target.getCharacterName() == null || target.getCharacterName().isBlank()
                ? character == null ? "꼬마 메모이" : character.getDisplayName() : target.getCharacterName();
        DashboardResponse.CharacterSummary characterSummary = character == null
                ? new DashboardResponse.CharacterSummary(1, characterDisplayName, "egg", 0, 100, 100, null)
                : new DashboardResponse.CharacterSummary(character.getLevel(), characterDisplayName, character.getStage(),
                character.getXpCurrent(), character.getXpGoal(), Math.max(0, character.getXpGoal() - character.getXpCurrent()), character.getSkinId());
        long unread = notificationRepository.countByRecipientUserIdAndReadFalse(userId);
        List<DashboardResponse.NotificationSummary> alerts = notificationRepository.findTop5ByRecipientUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toNotificationSummary).toList();
        CognitiveAnalysisEntity latestAnalysis = cognitiveAnalysisRepository.findAllByUserIdOrderByAnalyzedAtDesc(userId).stream().findFirst().orElse(null);
        DashboardResponse.CognitiveActivity activity = toCognitiveActivity(latestAnalysis, today);
        DashboardResponse.DiarySummary latestDiary = latestDiary(userId, today);
        List<DashboardResponse.Task> tasks = List.of(
                new DashboardResponse.Task("emotional_qa", "not_started", "AI 정서 문답", "오늘의 기억을 AI와 함께 이야기해요", "/ai"),
                new DashboardResponse.Task("memory_game", "new", "기억력 게임", "카드를 뒤집어 짝을 맞춰보세요", "/games/memory"),
                new DashboardResponse.Task("diary", "scheduled", "오늘의 일기", "오늘 대화를 일기로 남겨보세요", "/diary"));
        return new DashboardResponse(userId, target.getRole(), characterSummary, latestScreening, latestSummary, tasks,
                streak, new DashboardResponse.ActivitySummary(YearMonth.from(today).toString(), emotionalCount, gameCount, attendanceDays, streak),
                latestDiary, activity, unread, alerts);
    }

    @Transactional(readOnly = true)
    public GuardianReportResponse getGuardianReport(
            UUID authenticatedGuardianId,
            UUID guardianId,
            UUID elderId,
            LocalDate date,
            LocalDate fromDate,
            LocalDate toDate,
            String timezone
    ) {
        requireGuardian(authenticatedGuardianId, guardianId);
        requireGuardianAccess(guardianId, elderId, "screening");
        requireGuardianAccess(guardianId, elderId, "summary");
        if (!TIMEZONE.equals(timezone == null || timezone.isBlank() ? TIMEZONE : timezone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "timezone은 Asia/Seoul만 지원합니다.");
        }
        if (date != null && (fromDate != null || toDate != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "date와 from_date/to_date는 함께 사용할 수 없습니다.");
        }
        UserEntity elder = activeElder(elderId);
        List<CognitiveAnalysisEntity> analyses = cognitiveAnalysisRepository.findAllByUserIdOrderByAnalyzedAtDesc(elderId);
        CognitiveAnalysisEntity latest = analyses.stream().findFirst().orElse(null);
        SessionSummaryEntity latestSummary = sessionSummaryRepository.findAllByUserIdOrderByCreatedAtDesc(elderId).stream().findFirst().orElse(null);
        List<CognitiveAnalysisEntity> trendAnalyses = analyses.stream()
                .filter(analysis -> inDateRange(analysis.getAnalyzedAt(), fromDate, toDate))
                .toList();
        List<GuardianReportResponse.TrendPoint> trendPoints = trendPoints(trendAnalyses);
        Instant now = clock.instant();
        Instant sevenDaysAgo = now.minus(Duration.ofDays(7));
        List<SessionEntity> sessions = sessionRepository.findAllByUserIdOrderByStartedAtDesc(elderId);
        long sessionCount = sessions.stream().filter(session -> session.getStartedAt().isAfter(sevenDaysAgo)).count();
        long gameCount = gameResultRepository.countByUserIdAndPlayedAtBetweenAndCompletedTrue(elderId, sevenDaysAgo, now);
        List<GuardianReportResponse.Alert> alerts = notificationRepository.findAllByRecipientUserIdAndCreatedAtAfterOrderByCreatedAtDesc(elderId, sevenDaysAgo).stream()
                .filter(notification -> List.of("caution", "danger").contains(notification.getSeverity()))
                .map(notification -> new GuardianReportResponse.Alert(notification.getId(), notification.getTitle(), notification.getBody(), notification.getSeverity(), notification.getCreatedAt()))
                .toList();
        GuardianReportResponse.DailyReport daily = date == null ? null : dailyReport(elderId, date);
        BigDecimal latestScore = latest == null ? null : latest.getScreeningReferenceScore();
        return new GuardianReportResponse(elderId, elder.getName(), latestSummary == null ? null : latestSummary.getSummary(),
                latestScore, displayScore(latestScore), latestScore == null ? null : BigDecimal.valueOf(SCORE_MAX), rate(latestScore),
                latest == null ? null : latest.getRiskLevel(), latestSummary == null ? null : latestSummary.getVocabularyScore(),
                null, alertLevel(latest == null ? null : latest.getRiskLevel()), trend(trendAnalyses),
                sessions.stream().map(SessionEntity::getStartedAt).findFirst().orElse(null),
                new GuardianReportResponse.ActivitySummary(sessionCount, gameCount,
                        diaryRepository.findAllByUserIdOrderByWrittenAtDesc(elderId).stream()
                                .filter(diary -> diary.getWrittenAt().isAfter(sevenDaysAgo)).count()),
                trendPoints, alerts, daily);
    }

    @Transactional
    public ReportExportResponse requestExport(
            UUID authenticatedGuardianId,
            UUID guardianId,
            UUID elderId,
            LocalDate fromDate,
            LocalDate toDate,
            String format,
            String timezone
    ) {
        requireGuardian(authenticatedGuardianId, guardianId);
        requireGuardianAccess(guardianId, elderId, "screening");
        requireGuardianAccess(guardianId, elderId, "summary");
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "내보내기 기간을 확인하세요.");
        }
        String normalizedFormat = format == null ? "" : format.toLowerCase();
        if (!List.of("pdf", "csv").contains(normalizedFormat)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "format은 pdf 또는 csv여야 합니다.");
        }
        String normalizedTimezone = timezone == null || timezone.isBlank() ? TIMEZONE : timezone;
        if (!TIMEZONE.equals(normalizedTimezone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "timezone은 Asia/Seoul만 지원합니다.");
        }
        String requestKey = guardianId + ":" + elderId + ":" + fromDate + ":" + toDate + ":" + normalizedFormat + ":" + normalizedTimezone;
        ReportExportEntity existing = reportExportRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            return toExportResponse(existing);
        }
        Instant now = clock.instant();
        ReportExportEntity export = new ReportExportEntity(uuidGenerator.generate(), guardianId, elderId, requestKey,
                fromDate, toDate, normalizedFormat, normalizedTimezone, "processing", null, null, null, null, now, now);
        reportExportRepository.save(export);
        auditLogRepository.save(new AuditLogEntity(uuidGenerator.generate(), guardianId, elderId,
                "guardian_report_export_requested", "report_export", export.getId(),
                "{\"format\":\"" + normalizedFormat + "\"}", now));
        return toExportResponse(export);
    }

    private GuardianReportResponse.DailyReport dailyReport(UUID elderId, LocalDate date) {
        DailySummaryEntity summary = dailySummaryRepository.findByUserIdAndLocalDateAndTimezone(elderId, date, TIMEZONE).orElse(null);
        if (summary == null) {
            return null;
        }
        List<GuardianReportResponse.ConversationResult> conversations = readConversationResults(summary.getConversationResults()).stream()
                .map(node -> conversationResult(node, elderId)).filter(Objects::nonNull).toList();
        UUID diaryId = diaryRepository.findByDailySummaryId(summary.getId()).map(DiaryEntity::getId).orElse(null);
        return new GuardianReportResponse.DailyReport(summary.getLocalDate(), summary.getTimezone(), summary.getSessionCount(),
                summary.getAnalyzedSessionCount(), summary.getAnalysisStatus(), diaryId, conversations);
    }

    private GuardianReportResponse.ConversationResult conversationResult(JsonNode node, UUID elderId) {
        JsonNode sessionIdNode = node.get("session_id");
        if (sessionIdNode == null) return null;
        UUID sessionId;
        try { sessionId = UUID.fromString(sessionIdNode.asText()); } catch (IllegalArgumentException exception) { return null; }
        SessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !elderId.equals(session.getUserId())) return null;
        CognitiveAnalysisEntity analysis = latestForSession(sessionId);
        if (analysis == null) return null;
        return new GuardianReportResponse.ConversationResult(sessionId, session.getSessionType(), resultType(analysis.getRiskLevel()),
                safeDisplayLabel(analysis.getRiskLevel()), analysis.getScreeningReferenceScore(), readJson(analysis.getDomainScores()));
    }

    private ScreeningResultResponse toScreeningResponse(
            SessionEntity session,
            ScreeningResultEntity result,
            CognitiveAnalysisEntity analysis,
            UUID summaryId,
            boolean guardian
    ) {
        String status = result == null ? (analysis == null ? "pending" : "completed") : result.getStatus();
        String risk = result == null ? analysis == null ? null : analysis.getRiskLevel() : result.getRiskLevel();
        String displayLabel = result == null ? safeDisplayLabel(risk) : result.getDisplayLabel();
        String recommendation = result == null ? safeRecommendation(risk) : result.getRecommendation();
        String message = "completed".equals(status) ? "오늘 대화가 끝났어요. 다음 대화에서 만나요." : "분석 결과를 준비하고 있어요.";
        BigDecimal reference = result == null ? analysis == null ? null : analysis.getScreeningReferenceScore() : result.getScreeningReferenceScore();
        BigDecimal display = result == null ? displayScore(reference) : result.getDisplayScore();
        BigDecimal max = result == null ? reference == null ? null : BigDecimal.valueOf(SCORE_MAX) : result.getScoreMax();
        BigDecimal rateValue = result == null ? rate(reference) : result.getScoreRate();
        JsonNode domains = result == null ? analysis == null ? null : readJson(analysis.getDomainScores()) : readJson(result.getDomainScores());
        if (!guardian) {
            return new ScreeningResultResponse("elder", session.getId(), session.getUserId(), session.getSessionType(), status,
                    status.equals("completed") ? resultType(risk) : null, displayLabel, message, recommendation,
                    null, null, null, null, null, null, null,
                    result == null ? analysis == null ? null : analysis.getAnalyzedAt() : result.getCompletedAt(), summaryId);
        }
        return new ScreeningResultResponse("guardian", session.getId(), session.getUserId(), session.getSessionType(), status,
                status.equals("completed") ? resultType(risk) : null, displayLabel, message, recommendation,
                reference, display, max, rateValue, risk, safeScreeningLabel(risk), domains,
                result == null ? analysis == null ? null : analysis.getAnalyzedAt() : result.getCompletedAt(), summaryId);
    }

    private HistoryRecordResponse toHistoryRecord(
            CognitiveAnalysisEntity current,
            CognitiveAnalysisEntity previous,
            BigDecimal average30d,
            boolean guardian
    ) {
        BigDecimal reference = current.getScreeningReferenceScore();
        BigDecimal display = displayScore(reference);
        BigDecimal delta = previous == null || previous.getScreeningReferenceScore() == null || reference == null
                ? null : display.subtract(displayScore(previous.getScreeningReferenceScore())).setScale(2, RoundingMode.HALF_UP);
        if (!guardian) {
            return new HistoryRecordResponse(current.getId(), current.getSessionId(), null, null, null, null,
                    null, null, null, trendFor(delta), null, null, current.getAnalyzedAt());
        }
        return new HistoryRecordResponse(current.getId(), current.getSessionId(), reference, display,
                BigDecimal.valueOf(SCORE_MAX), rate(reference), current.getLabel(), current.getRiskLevel(),
                readJson(current.getDomainScores()), trendFor(delta), average30d, delta, current.getAnalyzedAt());
    }

    private List<CognitiveAnalysisEntity> groupAnalyses(List<CognitiveAnalysisEntity> source, String aggregation) {
        if ("answer".equals(aggregation)) return source;
        if ("session".equals(aggregation)) {
            return source.stream().collect(Collectors.toMap(CognitiveAnalysisEntity::getSessionId, Function.identity(),
                    (first, second) -> first, LinkedHashMap::new)).values().stream().toList();
        }
        if ("day".equals(aggregation)) {
            return source.stream().collect(Collectors.toMap(analysis -> analysis.getAnalyzedAt().atZone(BUSINESS_ZONE).toLocalDate(),
                    Function.identity(), (first, second) -> averageAnalysis(first, second), LinkedHashMap::new)).values().stream().toList();
        }
        return source.isEmpty() ? List.of() : List.of(source.stream().reduce(this::averageAnalysis).orElseThrow());
    }

    private CognitiveAnalysisEntity averageAnalysis(CognitiveAnalysisEntity first, CognitiveAnalysisEntity second) {
        BigDecimal a = first.getScreeningReferenceScore() == null ? BigDecimal.ZERO : first.getScreeningReferenceScore();
        BigDecimal b = second.getScreeningReferenceScore() == null ? BigDecimal.ZERO : second.getScreeningReferenceScore();
        return new CognitiveAnalysisEntity(first.getId(), first.getTranscriptId(), first.getAcousticAnalysisId(), first.getUserId(),
                first.getSessionId(), first.getQuestionId(), first.getQuestionType(), first.getFusionMode(), first.getModelName(),
                first.getModelVersion(), first.getLanguageReferenceScore(), a.add(b).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP),
                first.getLabel(), first.getRiskLevel(), first.getCognitiveFlags(), first.getDomainScores(), first.getModelBreakdown(),
                first.getAnalyzedAt(), first.getCreatedAt());
    }

    private List<GuardianReportResponse.TrendPoint> trendPoints(List<CognitiveAnalysisEntity> analyses) {
        Map<LocalDate, List<CognitiveAnalysisEntity>> byDate = analyses.stream().collect(Collectors.groupingBy(
                analysis -> analysis.getAnalyzedAt().atZone(BUSINESS_ZONE).toLocalDate(), LinkedHashMap::new, Collectors.toList()));
        List<GuardianReportResponse.TrendPoint> result = new ArrayList<>();
        BigDecimal previous = null;
        for (Map.Entry<LocalDate, List<CognitiveAnalysisEntity>> entry : byDate.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            BigDecimal score = averageScores(entry.getValue());
            BigDecimal display = displayScore(score);
            result.add(new GuardianReportResponse.TrendPoint(entry.getKey(), score, display, score == null ? null : BigDecimal.valueOf(SCORE_MAX),
                    rate(score), previous == null || display == null ? null : display.subtract(previous), riskFor(score)));
            previous = display;
        }
        return result;
    }

    private DashboardResponse.ScreeningSummary latestScreeningSummary(UUID sessionId, boolean guardian) {
        ScreeningResultEntity result = screeningResultRepository.findBySessionId(sessionId).orElse(null);
        CognitiveAnalysisEntity analysis = latestForSession(sessionId);
        if (result == null && analysis == null) return null;
        String risk = result == null ? analysis.getRiskLevel() : result.getRiskLevel();
        return new DashboardResponse.ScreeningSummary(sessionId, result == null ? "completed" : result.getStatus(), resultType(risk),
                safeDisplayLabel(risk), "오늘도 대화를 잘 이어가고 있어요.", safeRecommendation(risk),
                result == null ? analysis.getAnalyzedAt() : result.getCompletedAt());
    }

    private DashboardResponse.CognitiveActivity toCognitiveActivity(CognitiveAnalysisEntity analysis, LocalDate date) {
        String status = analysis == null || "normal".equals(analysis.getRiskLevel()) ? "stable"
                : "caution".equals(analysis.getRiskLevel()) ? "observe" : "attention_required";
        String label = "stable".equals(status) ? "안정적" : "observe".equals(status) ? "꾸준한 관찰" : "확인 필요";
        String message = "stable".equals(status) ? "현재 인지 활동이 안정적으로 유지되고 있어요." : "최근 활동을 천천히 확인해 주세요.";
        return new DashboardResponse.CognitiveActivity(status, label, label, message, date);
    }

    private DashboardResponse.DiarySummary latestDiary(UUID userId, LocalDate today) {
        DiaryGenerationJobEntity job = diaryGenerationJobRepository.findByUserIdAndTargetDate(userId, today)
                .orElseGet(() -> diaryGenerationJobRepository.findByUserIdAndTargetDate(userId, today.minusDays(1)).orElse(null));
        if (job != null) {
            String label = switch (job.getStatus()) {
                case "completed" -> "일기 생성 완료";
                case "processing" -> "일기 생성 중";
                case "failed" -> "일기 생성 실패";
                case "conversation_incomplete" -> "대화가 부족해요";
                default -> "일기 생성 예정";
            };
            return new DashboardResponse.DiarySummary(job.getTargetDate(), job.getStatus(), job.getDiaryId(), label,
                    "completed".equals(job.getStatus()) ? "오늘의 일기를 확인해 보세요." : "오늘 대화를 바탕으로 일기를 준비해요.", job.getAvailableAt());
        }
        DiaryEntity diary = diaryRepository.findAllByUserIdOrderByWrittenAtDesc(userId).stream().findFirst().orElse(null);
        if (diary == null) return new DashboardResponse.DiarySummary(today, "scheduled", null, "내일 일기 생성 예정", "오늘 대화를 바탕으로 내일 일기를 준비해요.", null);
        return new DashboardResponse.DiarySummary(localDate(diary.getWrittenAt()), "completed", diary.getId(), "일기 생성 완료", "오늘의 일기를 확인해 보세요.", diary.getCreatedAt());
    }

    private LocalDate localDate(Instant instant) {
        return instant.atZone(BUSINESS_ZONE).toLocalDate();
    }

    private DashboardResponse.NotificationSummary toNotificationSummary(NotificationEntity notification) {
        return new DashboardResponse.NotificationSummary(notification.getId(), notification.getTitle(), notification.getBody(),
                notification.getType(), notification.getSeverity(), notification.getStatusLabel(), notification.getCreatedAt());
    }

    private int attendanceStreak(List<SessionEntity> sessions, LocalDate today) {
        java.util.Set<LocalDate> dates = sessions.stream().map(session -> session.getStartedAt().atZone(BUSINESS_ZONE).toLocalDate()).collect(Collectors.toSet());
        int streak = 0;
        LocalDate cursor = today;
        while (dates.contains(cursor)) { streak++; cursor = cursor.minusDays(1); }
        return streak;
    }

    private boolean authorizeRead(UUID authenticatedUserId, UUID targetUserId, String scope) {
        if (authenticatedUserId.equals(targetUserId)) {
            activeElder(targetUserId);
            return false;
        }
        requireGuardianAccess(authenticatedUserId, targetUserId, scope);
        return true;
    }

    private void requireGuardianAccess(UUID guardianId, UUID elderId, String scope) {
        requireGuardian(guardianId, guardianId);
        activeElder(elderId);
        guardianAccessService.requireAccess(guardianId, elderId, scope);
    }

    private void requireGuardian(UUID authenticatedId, UUID guardianId) {
        if (!authenticatedId.equals(guardianId)) throw new AccessDeniedException("본인의 보호자 리포트만 조회할 수 있습니다.");
        UserEntity guardian = activeUser(guardianId);
        if (!"guardian".equals(guardian.getRole())) throw new AccessDeniedException("보호자 계정만 사용할 수 있습니다.");
    }

    private UserEntity activeElder(UUID userId) {
        UserEntity user = activeUser(userId);
        if (!"elder".equals(user.getRole())) throw new AccessDeniedException("고령자 계정만 분석 대상이 될 수 있습니다.");
        return user;
    }

    private UserEntity activeUser(UUID userId) {
        return userRepository.findById(userId).filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
    }

    private CognitiveAnalysisEntity latestForSession(UUID sessionId) {
        return cognitiveAnalysisRepository.findAllBySessionIdOrderByAnalyzedAtAsc(sessionId).stream().reduce((first, second) -> second).orElse(null);
    }

    private boolean inDateRange(Instant instant, LocalDate fromDate, LocalDate toDate) {
        LocalDate date = instant.atZone(BUSINESS_ZONE).toLocalDate();
        return (fromDate == null || !date.isBefore(fromDate)) && (toDate == null || !date.isAfter(toDate));
    }

    private boolean between(Instant instant, Instant from, Instant to) { return !instant.isBefore(from) && instant.isBefore(to); }

    private BigDecimal averageScores(List<CognitiveAnalysisEntity> analyses) {
        List<BigDecimal> scores = analyses.stream().map(CognitiveAnalysisEntity::getScreeningReferenceScore).filter(Objects::nonNull).toList();
        if (scores.isEmpty()) return null;
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(scores.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal displayScore(BigDecimal score) { return score == null ? null : score.multiply(BigDecimal.valueOf(SCORE_MAX)).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal rate(BigDecimal score) { return score == null ? null : score.setScale(6, RoundingMode.HALF_UP); }
    private String period(Instant instant, String aggregation) {
        YearMonth month = YearMonth.from(instant.atZone(BUSINESS_ZONE));
        return "quarter".equals(aggregation) ? month.getYear() + "-Q" + ((month.getMonthValue() - 1) / 3 + 1) : month.format(PERIOD_FORMAT);
    }
    private String trendFor(BigDecimal delta) { return delta == null || delta.abs().compareTo(BigDecimal.ONE) <= 0 ? "stable" : delta.signum() > 0 ? "improving" : "declining"; }
    private String trend(List<CognitiveAnalysisEntity> analyses) {
        if (analyses.size() < 2) return "stable";
        BigDecimal latest = analyses.get(0).getScreeningReferenceScore();
        BigDecimal oldest = analyses.get(analyses.size() - 1).getScreeningReferenceScore();
        return trendFor(displayScore(latest).subtract(displayScore(oldest)));
    }
    private String riskFor(BigDecimal score) { return score == null ? null : score.compareTo(new BigDecimal("0.75")) >= 0 ? "normal" : score.compareTo(new BigDecimal("0.50")) >= 0 ? "caution" : "warning"; }
    private String resultType(String risk) { return "warning".equals(risk) || "caution".equals(risk) ? "follow_up_recommended" : "positive_feedback"; }
    private String safeDisplayLabel(String risk) { return "warning".equals(risk) || "caution".equals(risk) ? "추가 확인을 권장해요" : "오늘 대화 결과가 좋아요"; }
    private String safeRecommendation(String risk) { return "warning".equals(risk) || "caution".equals(risk) ? "반복 관찰 결과를 확인하고 필요하면 전문기관 상담을 권장합니다." : null; }
    private String safeScreeningLabel(String risk) { return "warning".equals(risk) ? "확인이 필요한 상태" : "추이를 함께 살펴보세요"; }
    private String alertLevel(String risk) { return risk == null || "normal".equals(risk) ? "none" : risk; }
    private JsonNode readJson(String value) { try { return objectMapper.readTree(value == null ? "{}" : value); } catch (JsonProcessingException e) { return objectMapper.createObjectNode(); } }
    private List<JsonNode> readConversationResults(String value) { try { return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<>() { }); } catch (JsonProcessingException e) { return List.of(); } }
    private ReportExportResponse toExportResponse(ReportExportEntity export) {
        String url = export.getStorageKey() == null ? null : "/api/v1/guardian/exports/" + export.getId();
        return new ReportExportResponse(export.getId(), export.getStatus(), url, export.getExpiresAt(), export.getFailureReason());
    }
}
