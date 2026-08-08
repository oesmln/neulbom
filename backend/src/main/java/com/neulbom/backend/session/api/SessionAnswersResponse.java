package com.neulbom.backend.session.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionAnswersResponse(
        UUID sessionId,
        String sessionType,
        List<SessionAnswerItem> answers
) {
}
