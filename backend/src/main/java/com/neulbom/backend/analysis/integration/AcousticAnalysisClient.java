package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

public interface AcousticAnalysisClient {

    boolean isConfigured();

    AcousticResult analyze(
            String recordingId,
            SpeechToTextClient.AudioFile audioFile,
            int segmentLengthSec,
            String modelVersion
    );

    record AcousticResult(
            BigDecimal acousticReferenceScore,
            JsonNode acousticFlags,
            BigDecimal speechRate,
            BigDecimal pauseRatio,
            BigDecimal energyVariability,
            BigDecimal speechStability,
            String modelName,
            String modelVersion
    ) {
    }
}
