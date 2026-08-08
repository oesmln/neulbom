package com.neulbom.backend.session.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionAnswerItem(
        UUID answerId,
        UUID questionId,
        int questionOrder,
        String questionText,
        String answerText,
        UUID recordingId,
        Instant answeredAt
) {
}
