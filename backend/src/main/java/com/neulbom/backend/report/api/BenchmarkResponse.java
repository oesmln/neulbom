package com.neulbom.backend.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BenchmarkResponse(
        List<UserPoint> userSeries,
        List<RegionalPoint> regionalSeries,
        String sourceName,
        Instant sourceUpdatedAt
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UserPoint(String period, BigDecimal displayScore, BigDecimal scoreRate) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegionalPoint(
            String regionCode,
            String regionName,
            String period,
            BigDecimal averageDisplayScore,
            int sampleSize,
            boolean suppressed
    ) { }
}
