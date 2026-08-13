package com.neulbom.backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.guardian.GuardianAccessService;
import com.neulbom.backend.guardian.GuardianLinkEntity;
import com.neulbom.backend.guardian.GuardianLinkRepository;
import com.neulbom.backend.guardian.GuardianLinkScopeRepository;
import com.neulbom.backend.notification.api.NotificationPushRequest;
import com.neulbom.backend.notification.api.NotificationPushResponse;
import com.neulbom.backend.notification.api.NotificationReadResponse;
import com.neulbom.backend.notification.api.NotificationResponse;
import com.neulbom.backend.notification.api.NotificationsReadAllResponse;
import com.neulbom.backend.notification.api.NotificationsResponse;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserPreferenceEntity;
import com.neulbom.backend.user.UserPreferenceRepository;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인앱 알림의 저장과 수신자별 읽음 상태를 담당한다.
 *
 * <p>알림 저장은 OS push 설정과 분리한다. 따라서 push가 꺼진 사용자도 앱
 * 안에서 알림 이력을 확인할 수 있고, 실제 OS push adapter가 연결될 때
 * {@link #isOsPushEnabled(UUID, String)} 결과만 전달 조건으로 사용한다.</p>
 */
@Service
public class NotificationService {

    public static final String TYPE_SCREENING_ALERT = "screening_alert";
    public static final String TYPE_SCREENING_UPDATED = "screening_updated";
    public static final String TYPE_SESSION_COMPLETE = "session_complete";
    public static final String TYPE_SUMMARY = "summary";
    public static final String TYPE_DIARY_GENERATED = "diary_generated";
    public static final String TYPE_DIARY_GENERATION_FAILED = "diary_generation_failed";
    public static final String TYPE_REMINDER = "reminder";
    public static final String TYPE_CAMPAIGN = "campaign";
    public static final String TYPE_WEEKLY_REPORT = "weekly_report";
    public static final String TYPE_GUARDIAN_REACTION = "guardian_reaction";
    public static final String TYPE_APPOINTMENT_UPDATED = "appointment_updated";

    private static final Set<String> TYPES = Set.of(
            TYPE_SCREENING_ALERT,
            TYPE_SCREENING_UPDATED,
            TYPE_SESSION_COMPLETE,
            TYPE_SUMMARY,
            TYPE_DIARY_GENERATED,
            TYPE_DIARY_GENERATION_FAILED,
            TYPE_REMINDER,
            TYPE_CAMPAIGN,
            TYPE_WEEKLY_REPORT,
            TYPE_GUARDIAN_REACTION,
            TYPE_APPOINTMENT_UPDATED);
    private static final Set<String> SEVERITIES = Set.of("info", "success", "caution", "danger");
    private static final Set<String> REFERENCE_TYPES = Set.of("session", "screening", "diary", "report", "campaign", "appointment");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final GuardianLinkScopeRepository guardianLinkScopeRepository;
    private final GuardianAccessService guardianAccessService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            UserPreferenceRepository userPreferenceRepository,
            GuardianLinkRepository guardianLinkRepository,
            GuardianLinkScopeRepository guardianLinkScopeRepository,
            GuardianAccessService guardianAccessService,
            UuidGenerator uuidGenerator,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.guardianLinkRepository = guardianLinkRepository;
        this.guardianLinkScopeRepository = guardianLinkScopeRepository;
        this.guardianAccessService = guardianAccessService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationPushResponse push(NotificationPushRequest request) {
        String severity = normalizeSeverity(request.severity());
        String eventKey = eventKey(request.data());
        String type = request.type().trim();
        NotificationEntity notification = createForUser(
                request.targetUserId(),
                request.title().trim(),
                request.body().trim(),
                type,
                severity,
                normalizeStatusLabel(request.statusLabel()),
                request.data(),
                eventKey);
        return new NotificationPushResponse(notification.getId(), notification.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public NotificationsResponse list(
            UUID authenticatedUserId,
            UUID requestedUserId,
            boolean unreadOnly,
            String type,
            int limit
    ) {
        requireOwner(authenticatedUserId, requestedUserId);
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "limit은 1~100이어야 합니다.");
        }
        if (type != null && !type.isBlank()) validateType(type);
        String normalizedType = type == null || type.isBlank() ? null : type;
        List<NotificationEntity> all = findNotifications(requestedUserId, unreadOnly, normalizedType);
        List<NotificationResponse> page = all.stream()
                .limit(limit)
                .map(this::toResponse)
                .toList();
        return new NotificationsResponse(
                page,
                notificationRepository.countByRecipientUserIdAndReadFalse(requestedUserId));
    }

    @Transactional
    public NotificationReadResponse markRead(UUID authenticatedUserId, UUID notificationId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다."));
        if (!authenticatedUserId.equals(notification.getRecipientUserId())) {
            throw new AccessDeniedException("본인에게 전달된 알림만 읽을 수 있습니다.");
        }
        if (!notification.isRead()) {
            notification.markRead(clock.instant());
            notificationRepository.save(notification);
        }
        return new NotificationReadResponse(notification.getId(), notification.isRead(), notification.getReadAt());
    }

    @Transactional
    public NotificationsReadAllResponse markAllRead(UUID authenticatedUserId) {
        requireActiveUser(authenticatedUserId);
        Instant readAt = clock.instant();
        List<NotificationEntity> unread = notificationRepository
                .findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(authenticatedUserId);
        unread.forEach(notification -> notification.markRead(readAt));
        if (!unread.isEmpty()) notificationRepository.saveAll(unread);
        return new NotificationsReadAllResponse(unread.size(), readAt);
    }

    /** Creates an idempotent notification for a domain event. */
    @Transactional
    public NotificationEntity createForUser(
            UUID recipientUserId,
            String title,
            String body,
            String type,
            String severity,
            String statusLabel,
            JsonNode data,
            String eventKey
    ) {
        requireActiveUser(recipientUserId);
        validateType(type);
        String normalizedSeverity = normalizeSeverity(severity);
        validateData(data);
        String normalizedEventKey = normalizeEventKey(eventKey);
        if (normalizedEventKey != null) {
            NotificationEntity existing = notificationRepository
                    .findByRecipientUserIdAndEventKey(recipientUserId, normalizedEventKey)
                    .orElse(null);
            if (existing != null) return existing;
        }
        Instant now = clock.instant();
        NotificationEntity notification = new NotificationEntity(
                uuidGenerator.generate(),
                recipientUserId,
                requireText(title, "title"),
                requireText(body, "body"),
                type,
                normalizedSeverity,
                normalizeStatusLabel(statusLabel),
                serializeData(withEventId(data, normalizedEventKey)),
                normalizedEventKey,
                now);
        return notificationRepository.save(notification);
    }

    public boolean isOsPushEnabled(UUID recipientUserId, String type) {
        UserPreferenceEntity preferences = userPreferenceRepository.findById(recipientUserId).orElse(null);
        if (preferences == null) return true;
        if (!preferences.isPushNotificationEnabled()) return false;
        return switch (type) {
            case TYPE_GUARDIAN_REACTION -> preferences.isGuardianReactionNotificationEnabled();
            case TYPE_SCREENING_ALERT, TYPE_SCREENING_UPDATED -> preferences.isScreeningNotificationEnabled();
            case TYPE_DIARY_GENERATED, TYPE_DIARY_GENERATION_FAILED -> preferences.isDiaryNotificationEnabled();
            case TYPE_WEEKLY_REPORT -> preferences.isWeeklyReportNotificationEnabled();
            default -> true;
        };
    }

    @Transactional
    public NotificationEntity notifyScreening(
            UUID elderId,
            UUID analysisId,
            UUID sessionId,
            String riskLevel,
            String displayLabel
    ) {
        boolean warning = "warning".equals(riskLevel);
        boolean caution = "caution".equals(riskLevel);
        String type = warning || caution ? TYPE_SCREENING_ALERT : TYPE_SCREENING_UPDATED;
        String severity = warning ? "danger" : caution ? "caution" : "success";
        String title = warning ? "인지 점수 하락 경보" : "인지 활동 결과 업데이트";
        String body = warning
                ? "최근 인지 활동에서 확인이 필요한 변화가 있어요."
                : caution ? "최근 인지 활동 결과를 확인해 보세요." : "인지 활동 결과가 업데이트되었어요.";
        ObjectNode data = referenceData("/screenings/" + sessionId + "/result", "screening", analysisId, elderId);
        NotificationEntity notification = createForUser(
                elderId, title, body, type, severity, displayLabel, data, "screening:" + analysisId);
        if (warning || caution) {
            notifyGuardians(
                    elderId,
                    "인지 활동 확인이 필요해요",
                    "연결된 어르신의 인지 활동 결과를 확인해 주세요.",
                    TYPE_SCREENING_ALERT,
                    severity,
                    displayLabel,
                    data,
                    "screening-alert:" + analysisId,
                    "screening");
        }
        return notification;
    }

    @Transactional
    public NotificationEntity notifySessionSummary(UUID elderId, UUID summaryId, UUID sessionId) {
        ObjectNode data = referenceData("/ai/summary/" + sessionId, "session", sessionId, elderId);
        return createForUser(
                elderId,
                "AI 대화 요약 완료",
                "오늘 대화 요약을 확인해 보세요.",
                TYPE_SUMMARY,
                "success",
                "완료",
                data,
                "session-summary:" + summaryId);
    }

    @Transactional
    public NotificationEntity notifyDailySummary(UUID elderId, UUID summaryId) {
        ObjectNode data = referenceData("/diary", "report", summaryId, elderId);
        return createForUser(
                elderId,
                "오늘의 대화 집계 완료",
                "오늘 활동을 바탕으로 일기를 만들 수 있어요.",
                TYPE_SUMMARY,
                "success",
                "완료",
                data,
                "daily-summary:" + summaryId);
    }

    @Transactional
    public NotificationEntity notifyDiaryGenerated(UUID elderId, UUID diaryId) {
        ObjectNode data = referenceData("/diaries/" + diaryId, "diary", diaryId, elderId);
        return createForUser(
                elderId,
                "일기 생성 완료",
                "어제 대화를 바탕으로 오늘 일기가 업데이트되었어요.",
                TYPE_DIARY_GENERATED,
                "success",
                "완료",
                data,
                "diary-generated:" + diaryId);
    }

    @Transactional
    public NotificationEntity notifyDiaryGenerationFailed(UUID elderId, UUID jobId, String reason) {
        ObjectNode data = referenceData("/diary", "diary", jobId, elderId);
        data.put("failure_reason", reason == null ? "unknown" : reason);
        return createForUser(
                elderId,
                "일기 생성 미완료",
                "일기 생성이 완료되지 않았어요. 잠시 후 다시 확인해 주세요.",
                TYPE_DIARY_GENERATION_FAILED,
                "caution",
                "주의",
                data,
                "diary-generation-failed:" + jobId);
    }

    @Transactional
    public NotificationEntity notifyGuardianReaction(UUID elderId, UUID diaryId, UUID reactionId) {
        ObjectNode data = referenceData("/diaries/" + diaryId, "diary", diaryId, elderId);
        data.put("reaction_id", reactionId.toString());
        return createForUser(
                elderId,
                "보호자 반응이 도착했어요",
                "보호자가 일기에 반응을 남겼어요.",
                TYPE_GUARDIAN_REACTION,
                "info",
                "새 소식",
                data,
                "guardian-reaction:" + reactionId);
    }

    @Transactional
    public NotificationEntity notifyGameCompleted(UUID elderId, UUID resultId, UUID sessionId) {
        ObjectNode data = referenceData("/game/history", "session", sessionId, elderId);
        return createForUser(
                elderId,
                "기억력 게임 완료",
                "게임 결과와 경험치가 기록되었어요.",
                TYPE_SESSION_COMPLETE,
                "success",
                "완료",
                data,
                "game-complete:" + resultId);
    }

    /**
     * 주간 리포트 워커가 매주 월요일 09:00(Asia/Seoul)에 호출하는 생성 경계다.
     * 실제 스케줄러와 OS push adapter는 운영 인프라 연결 단계에서 이 메서드를 사용한다.
     */
    @Transactional
    public NotificationEntity notifyWeeklyReport(
            UUID guardianId,
            UUID reportId,
            UUID elderId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        ObjectNode data = referenceData("/guardian/report?elder_id=" + elderId, "report", reportId, elderId);
        data.put("from_date", fromDate.toString());
        data.put("to_date", toDate.toString());
        return createForUser(
                guardianId,
                "주간 리포트가 준비되었어요",
                "연결된 어르신의 이번 주 활동과 인지 추이를 확인해 보세요.",
                TYPE_WEEKLY_REPORT,
                "info",
                "새 리포트",
                data,
                "weekly-report:" + reportId + ":" + guardianId);
    }

    @Transactional
    public NotificationEntity notifyAppointmentUpdated(UUID guardianId, UUID appointmentId, String status) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("target_route", "/counseling/appointments");
        data.put("reference_type", "appointment");
        data.put("reference_id", appointmentId.toString());
        data.put("appointment_status", status);
        String title = "상담 예약이 업데이트되었어요";
        String body = switch (status) {
            case "cancelled" -> "상담 예약이 취소되었어요.";
            case "updated" -> "상담 예약 내용이 변경되었어요.";
            default -> "상담 예약 신청이 접수되었어요.";
        };
        return createForUser(
                guardianId,
                title,
                body,
                TYPE_APPOINTMENT_UPDATED,
                "info",
                status,
                data,
                "appointment:" + appointmentId + ":" + status);
    }

    private void notifyGuardians(
            UUID elderId,
            String title,
            String body,
            String type,
            String severity,
            String statusLabel,
            JsonNode data,
            String eventKeyPrefix,
            String requiredScope
    ) {
        if (!guardianAccessService.hasAgreedGuardianConsent(elderId)) return;
        for (GuardianLinkEntity link : guardianLinkRepository.findAllByElderIdAndStatus(elderId, GuardianLinkEntity.ACTIVE)) {
            if (!hasScope(link.getId(), requiredScope)) continue;
            UserEntity guardian = userRepository.findById(link.getGuardianId())
                    .filter(UserEntity::isActive)
                    .filter(user -> "guardian".equals(user.getRole()))
                    .orElse(null);
            if (guardian == null) continue;
            createForUser(
                    guardian.getId(),
                    title,
                    body,
                    type,
                    severity,
                    statusLabel,
                    data,
                    eventKeyPrefix + ":" + guardian.getId());
        }
    }

    private List<NotificationEntity> findNotifications(UUID userId, boolean unreadOnly, String type) {
        if (type == null && unreadOnly) {
            return notificationRepository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        }
        if (type != null && unreadOnly) {
            return notificationRepository.findAllByRecipientUserIdAndReadFalseAndTypeOrderByCreatedAtDesc(userId, type);
        }
        if (type != null) {
            return notificationRepository.findAllByRecipientUserIdAndTypeOrderByCreatedAtDesc(userId, type);
        }
        return notificationRepository.findAllByRecipientUserIdOrderByCreatedAtDesc(userId);
    }

    private NotificationResponse toResponse(NotificationEntity notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getType(),
                notification.getSeverity(),
                notification.getStatusLabel(),
                parseData(notification.getData()),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    private JsonNode withEventId(JsonNode data, String eventKey) {
        if (data == null || data.isNull()) return null;
        JsonNode copy = data.deepCopy();
        if (eventKey == null) return copy;
        if (!copy.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "data는 JSON object여야 합니다.");
        }
        ((ObjectNode) copy).put("event_id", eventKey);
        return copy;
    }

    private ObjectNode referenceData(String route, String referenceType, UUID referenceId, UUID elderId) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("target_route", route);
        data.put("reference_type", referenceType);
        data.put("reference_id", referenceId.toString());
        data.put("elder_id", elderId.toString());
        return data;
    }

    private boolean hasScope(UUID linkId, String requiredScope) {
        return guardianLinkScopeRepository.findAllByIdLinkId(linkId).stream()
                .map(scope -> scope.getId().getScope())
                .anyMatch(scope -> "all".equals(scope) || requiredScope.equals(scope));
    }

    private JsonNode parseData(String data) {
        if (data == null || data.isBlank()) return null;
        try {
            return objectMapper.readTree(data);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String serializeData(JsonNode data) {
        if (data == null || data.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "알림 저장에 실패했습니다.", "알림 데이터를 직렬화할 수 없습니다.");
        }
    }

    private void validateData(JsonNode data) {
        if (data == null || data.isNull()) return;
        if (!data.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "data는 JSON object여야 합니다.");
        }
        JsonNode referenceType = data.get("reference_type");
        if (referenceType != null && !referenceType.isNull()
                && (!referenceType.isTextual() || !REFERENCE_TYPES.contains(referenceType.asText()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "data.reference_type 허용값을 확인하세요.");
        }
    }

    private String eventKey(JsonNode data) {
        if (data == null || !data.isObject()) return null;
        JsonNode eventId = data.get("event_id");
        return eventId != null && eventId.isTextual() ? normalizeEventKey(eventId.asText()) : null;
    }

    private String normalizeEventKey(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) return null;
        String normalized = eventKey.trim();
        if (normalized.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "data.event_id는 255자 이하여야 합니다.");
        }
        return normalized;
    }

    private String normalizeSeverity(String severity) {
        String normalized = severity == null || severity.isBlank() ? "info" : severity.trim();
        if (!SEVERITIES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "severity 허용값을 확인하세요.");
        }
        return normalized;
    }

    private String normalizeStatusLabel(String statusLabel) {
        return statusLabel == null || statusLabel.isBlank() ? null : statusLabel.trim();
    }

    private void validateType(String type) {
        if (type == null || !TYPES.contains(type.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "type 허용값을 확인하세요.");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", field + "을(를) 확인하세요.");
        }
        return value.trim();
    }

    private void requireOwner(UUID authenticatedUserId, UUID requestedUserId) {
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new AccessDeniedException("본인 알림만 조회할 수 있습니다.");
        }
        requireActiveUser(requestedUserId);
    }

    private UserEntity requireActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
    }
}
