package com.neulbom.backend.recording;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transcripts")
public class TranscriptEntity {

    @Id
    private UUID id;

    @Column(name = "recording_id", nullable = false, unique = true)
    private UUID recordingId;

    @Column(columnDefinition = "text")
    private String transcript;

    @Column(name = "duration_sec", precision = 8, scale = 3)
    private BigDecimal durationSec;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
