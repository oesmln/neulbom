package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.time.Instant;

public record DailyEstimateCompletion(
        BigDecimal estimatedModelScore,
        BigDecimal scoreDelta,
        String modelVersion,
        String thresholdVersion,
        String riskLevel,
        String resultJson,
        Instant analyzedAt,
        String outputSnapshot
) { }
