package com.neulbom.backend.recording.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecordingUploadResponse(
        UUID recordingId,
        UUID clientRecordingId,
        String purpose,
        String syncStatus,
        String transcriptStatus,
        String analysisStatus,
        boolean deduplicated
) {
}
