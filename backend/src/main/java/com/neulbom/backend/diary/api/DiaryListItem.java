package com.neulbom.backend.diary.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DiaryListItem(
        UUID diaryId,
        String title,
        String preview,
        String sourceType,
        String mood,
        Integer moodLevel,
        Instant writtenAt,
        long reactionCount
) {
}
