package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;
import java.util.List;

import com.neulbom.backend.analysis.api.QaPair;

public interface SessionSummaryClient {

    boolean isConfigured();

    SummaryResult summarize(List<QaPair> qaPairs);

    record SummaryResult(
            String summary,
            BigDecimal vocabularyScore,
            List<String> keywords,
            String modelName,
            String modelVersion
    ) {
    }
}
