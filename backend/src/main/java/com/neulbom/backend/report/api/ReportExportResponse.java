package com.neulbom.backend.report.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReportExportResponse(
        UUID exportId,
        String status,
        String downloadUrl,
        Instant expiresAt,
        String failureReason
) {
}
