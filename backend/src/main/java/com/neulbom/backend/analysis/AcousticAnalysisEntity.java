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
}
