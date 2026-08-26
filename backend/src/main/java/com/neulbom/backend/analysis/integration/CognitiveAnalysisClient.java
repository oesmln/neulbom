package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public interface CognitiveAnalysisClient {

    boolean isConfigured();

    CognitiveResult analyze(
            UUID questionId,
            String question,
            String transcript,
            String questionType,
            String modelVersion
    );

    record CognitiveResult(
            BigDecimal languageReferenceScore,
            JsonNode cognitiveFlags,
            JsonNode domainScores,
            JsonNode modelBreakdown,
            String modelName,
            String modelVersion
    ) {
    }
}
