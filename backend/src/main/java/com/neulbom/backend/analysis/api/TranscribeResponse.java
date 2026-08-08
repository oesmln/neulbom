package com.neulbom.backend.analysis.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TranscribeResponse(
        UUID transcriptId,
        UUID recordingId,
        String transcript,
        BigDecimal durationSec,
        BigDecimal confidence,
        String language,
        String model
) {
}
