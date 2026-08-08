package com.neulbom.backend.diary.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DiaryDetailResponse(
        UUID diaryId,
        UUID userId,
        String sourceType,
        String title,
        String content,
        UUID sessionId,
        UUID dailySummaryId,
        String mood,
        Integer moodLevel,
        Instant writtenAt,
        Instant createdAt,
        Instant updatedAt,
        List<ReactionResponse> reactions
) {
}
