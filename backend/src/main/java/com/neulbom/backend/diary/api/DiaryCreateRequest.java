package com.neulbom.backend.diary.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DiaryCreateRequest(
        @NotNull UUID userId,
        @NotBlank String sourceType,
        String title,
        @NotBlank String content,
        UUID recordingId,
        UUID sessionId,
        String mood,
        Integer moodLevel,
        @NotNull Instant writtenAt
) {
}
