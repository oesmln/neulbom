package com.neulbom.backend.analysis.integration;

import java.math.BigDecimal;

public interface SpeechToTextClient {

    boolean isConfigured();

    TranscriptionResult transcribe(AudioFile audioFile);

    record AudioFile(
            byte[] content,
            String filename,
            String contentType
    ) {
    }

    record TranscriptionResult(
            String transcript,
            BigDecimal durationSec,
            BigDecimal confidence,
            String language,
            String modelName,
            String modelVersion
    ) {
    }
}
