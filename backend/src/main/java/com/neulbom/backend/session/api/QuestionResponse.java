package com.neulbom.backend.session.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QuestionResponse(
        UUID questionId,
        String content,
        String type,
        int order,
        String hint,
        boolean subtitleAvailable
) {
}
