package com.neulbom.backend.diary.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DiaryFromSessionRequest(
        @NotNull UUID sessionId,
        @NotNull UUID userId,
        UUID summaryId,
        String title,
        String content,
        String mood,
        Integer moodLevel
) {
}
