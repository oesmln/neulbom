package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cognitive_feature_snapshots")
public class CognitiveFeatureSnapshotEntity {

    @Id
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_session_id", nullable = false, unique = true)
    private UUID sourceSessionId;

    @Column(name = "source_analysis_id", nullable = false, unique = true)
    private UUID sourceAnalysisId;

    @Column(name = "question_set_version", nullable = false, length = 120)
    private String questionSetVersion;

    @Column(name = "model_version", nullable = false, length = 120)
    private String modelVersion;

    @Column(name = "threshold_version", nullable = false, length = 120)
    private String thresholdVersion;

    @Column(name = "baseline_model_score", nullable = false, precision = 12, scale = 10)
    private BigDecimal baselineModelScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_snapshot", nullable = false, columnDefinition = "jsonb")
    private String featureSnapshot;

    @Column(name = "source_analyzed_at", nullable = false)
    private Instant sourceAnalyzedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CognitiveFeatureSnapshotEntity() { }

    public CognitiveFeatureSnapshotEntity(UUID snapshotId, UUID userId, UUID sourceSessionId,
            UUID sourceAnalysisId, String questionSetVersion, String modelVersion,
            String thresholdVersion, BigDecimal baselineModelScore, String featureSnapshot,
            Instant sourceAnalyzedAt, Instant createdAt) {
        this.snapshotId = snapshotId;
        this.userId = userId;
        this.sourceSessionId = sourceSessionId;
        this.sourceAnalysisId = sourceAnalysisId;
        this.questionSetVersion = questionSetVersion;
        this.modelVersion = modelVersion;
        this.thresholdVersion = thresholdVersion;
        this.baselineModelScore = baselineModelScore;
        this.featureSnapshot = featureSnapshot;
        this.sourceAnalyzedAt = sourceAnalyzedAt;
        this.createdAt = createdAt;
    }

    public UUID getSnapshotId() { return snapshotId; }
    public UUID getUserId() { return userId; }
    public UUID getSourceSessionId() { return sourceSessionId; }
    public UUID getSourceAnalysisId() { return sourceAnalysisId; }
    public String getQuestionSetVersion() { return questionSetVersion; }
    public String getModelVersion() { return modelVersion; }
    public String getThresholdVersion() { return thresholdVersion; }
    public BigDecimal getBaselineModelScore() { return baselineModelScore; }
    public String getFeatureSnapshot() { return featureSnapshot; }
    public Instant getSourceAnalyzedAt() { return sourceAnalyzedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
