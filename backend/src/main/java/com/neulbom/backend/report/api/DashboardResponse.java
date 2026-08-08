package com.neulbom.backend.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DashboardResponse(
        UUID userId,
        String role,
        CharacterSummary character,
        ScreeningSummary latestScreening,
        Summary latestSummary,
        List<Task> todayTasks,
        int conversationStreakDays,
        ActivitySummary monthlyActivity,
        DiarySummary latestDiary,
        CognitiveActivity cognitiveActivity,
        long unreadNotificationCount,
        List<NotificationSummary> recentAlerts
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CharacterSummary(
            int level,
            String displayName,
            String stage,
            int xpCurrent,
            int xpGoal,
            int xpRemaining,
            String skinId
    ) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ScreeningSummary(
            UUID sessionId,
            String resultStatus,
            String resultType,
            String displayLabel,
            String message,
            String recommendation,
            Instant completedAt
    ) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Summary(UUID summaryId, UUID sessionId, String summary, Instant createdAt) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Task(String taskType, String status, String title, String description, String targetRoute) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ActivitySummary(
            String yearMonth,
            long emotionalQaCompletedCount,
            long gameCompletedCount,
            int attendanceDays,
            int currentAttendanceStreakDays
    ) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DiarySummary(
            LocalDate targetDate,
            String generationStatus,
            UUID diaryId,
            String displayLabel,
            String message,
            Instant availableAt
    ) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CognitiveActivity(String status, String displayLabel, String title, String message, LocalDate referenceDate) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NotificationSummary(
            UUID notificationId,
            String title,
            String body,
            String type,
            String severity,
            String statusLabel,
            Instant createdAt
    ) { }
}
