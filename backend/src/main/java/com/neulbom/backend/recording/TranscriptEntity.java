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

    public TranscriptEntity(
            UUID id,
            UUID recordingId,
            String transcript,
            BigDecimal durationSec,
            BigDecimal confidence,
            String language,
            String modelName,
            String modelVersion,
            String status,
            Instant analyzedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.recordingId = recordingId;
        this.transcript = transcript;
        this.durationSec = durationSec;
        this.confidence = confidence;
        this.language = language;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.status = status;
        this.analyzedAt = analyzedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    protected TranscriptEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecordingId() {
        return recordingId;
    }

    public String getTranscript() {
        return transcript;
    }

    public BigDecimal getDurationSec() {
        return durationSec;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getLanguage() {
        return language;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getStatus() {
        return status;
    }
}
