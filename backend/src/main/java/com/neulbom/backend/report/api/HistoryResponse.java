package com.neulbom.backend.report.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HistoryResponse(
        List<HistoryRecordResponse> records,
        int total,
        String aggregation,
        boolean sampleSufficient
) {
}
