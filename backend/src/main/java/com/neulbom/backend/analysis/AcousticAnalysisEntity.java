package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "acoustic_analyses")
public class AcousticAnalysisEntity {

    @Id
    private UUID id;

    @Column(name = "recording_id", nullable = false)
    private UUID recordingId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "acoustic_reference_score", precision = 8, scale = 6)
    private BigDecimal acousticReferenceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acoustic_flags", columnDefinition = "jsonb")
    private String acousticFlags;

    @Column(name = "speech_rate", precision = 10, scale = 4)
    private BigDecimal speechRate;

    @Column(name = "pause_ratio", precision = 8, scale = 6)
    private BigDecimal pauseRatio;

    @Column(name = "energy_variability", precision = 10, scale = 4)
    private BigDecimal energyVariability;

    @Column(name = "speech_stability", precision = 10, scale = 4)
    private BigDecimal speechStability;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AcousticAnalysisEntity() {
    }

    public AcousticAnalysisEntity(
            UUID id,
            UUID recordingId,
            String modelName,
            String modelVersion,
            BigDecimal acousticReferenceScore,
            String acousticFlags,
            BigDecimal speechRate,
            BigDecimal pauseRatio,
            BigDecimal energyVariability,
            BigDecimal speechStability,
            Instant analyzedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.recordingId = recordingId;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.acousticReferenceScore = acousticReferenceScore;
        this.acousticFlags = acousticFlags;
        this.speechRate = speechRate;
        this.pauseRatio = pauseRatio;
        this.energyVariability = energyVariability;
        this.speechStability = speechStability;
        this.analyzedAt = analyzedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRecordingId() { return recordingId; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public BigDecimal getAcousticReferenceScore() { return acousticReferenceScore; }
    public String getAcousticFlags() { return acousticFlags; }
    public BigDecimal getSpeechRate() { return speechRate; }
    public BigDecimal getPauseRatio() { return pauseRatio; }
    public BigDecimal getEnergyVariability() { return energyVariability; }
    public BigDecimal getSpeechStability() { return speechStability; }
    public Instant getAnalyzedAt() { return analyzedAt; }
}
