package com.neulbom.backend.analysis.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DailySummariesResponse(List<DailySummaryResponse> dailySummaries, long total, int page, int limit) {
}
