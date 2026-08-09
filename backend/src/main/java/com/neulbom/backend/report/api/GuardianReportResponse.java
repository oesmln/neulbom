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
public record GuardianReportResponse(
        UUID elderId,
        String elderName,
        String latestSummary,
        BigDecimal latestScreeningScore,
        BigDecimal latestDisplayScore,
        BigDecimal latestScoreMax,
        BigDecimal latestScoreRate,
        String latestRiskLevel,
        BigDecimal vocabularyScore,
        BigDecimal gameCognitiveIndex,
        String alertLevel,
        String trend30d,
        Instant lastSessionAt,
        ActivitySummary activitySummary7d,
        List<TrendPoint> trendPoints,
        List<Alert> recentAlerts,
        DailyReport dailySummary
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ActivitySummary(long sessionCount, long gameCount, long diaryCount) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TrendPoint(
            LocalDate date,
            BigDecimal screeningReferenceScore,
            BigDecimal displayScore,
            BigDecimal scoreMax,
            BigDecimal scoreRate,
            BigDecimal scoreDelta,
            String riskLevel
    ) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Alert(UUID notificationId, String title, String body, String severity, Instant createdAt) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DailyReport(
            LocalDate localDate,
            String timezone,
            int sessionCount,
            int analyzedSessionCount,
            String analysisStatus,
            UUID diaryId,
            List<ConversationResult> conversationResults
    ) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ConversationResult(
            UUID sessionId,
            String sessionType,
            String resultType,
            String displayLabel,
            BigDecimal screeningReferenceScore,
            JsonNode domainScores
    ) { }
}
