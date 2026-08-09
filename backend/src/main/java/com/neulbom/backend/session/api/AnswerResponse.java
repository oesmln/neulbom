package com.neulbom.backend.session.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnswerResponse(
        UUID answerId,
        UUID questionId,
        boolean saved,
        int nextQuestionOrder,
        String syncStatus
) {
}
