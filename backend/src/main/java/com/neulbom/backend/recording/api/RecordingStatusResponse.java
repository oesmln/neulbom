package com.neulbom.backend.recording.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecordingStatusResponse(
        UUID recordingId,
        UUID clientRecordingId,
        String purpose,
        UUID sessionId,
        UUID questionId,
        String syncStatus,
        String transcriptStatus,
        UUID transcriptId,
        UUID acousticAnalysisId,
        UUID cognitiveAnalysisId,
        String errorMessage,
        Instant updatedAt
) {
}
