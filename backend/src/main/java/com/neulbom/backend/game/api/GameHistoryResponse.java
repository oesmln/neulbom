package com.neulbom.backend.game.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GameHistoryResponse(List<GameHistoryItem> records, int total, int limit) {
}
