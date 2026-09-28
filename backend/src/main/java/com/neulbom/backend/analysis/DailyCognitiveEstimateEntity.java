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
@Table(name = "daily_cognitive_estimates")
public class DailyCognitiveEstimateEntity {

    @Id
    @Column(name = "estimate_id")
    private UUID estimateId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;
    @Column(name = "baseline_snapshot_id", nullable = false)
    private UUID baselineSnapshotId;
    @Column(name = "parent_estimate_id")
    private UUID parentEstimateId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "estimate_type", nullable = false, length = 50)
    private String estimateType;
    @Column(name = "estimated_model_score", precision = 12, scale = 10)
    private BigDecimal estimatedModelScore;
    @Column(name = "baseline_model_score", nullable = false, precision = 12, scale = 10)
    private BigDecimal baselineModelScore;
    @Column(name = "score_delta", precision = 12, scale = 10)
    private BigDecimal scoreDelta;
    @Column(name = "model_version", length = 120)
    private String modelVersion;
    @Column(name = "threshold_version", length = 120)
    private String thresholdVersion;
    @Column(name = "risk_level", length = 40)
    private String riskLevel;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_feature_snapshot", columnDefinition = "jsonb")
    private String outputFeatureSnapshot;
    @Column(name = "analyzed_at")
    private Instant analyzedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyCognitiveEstimateEntity() { }

    public DailyCognitiveEstimateEntity(UUID estimateId, UUID userId, UUID sessionId,
            UUID baselineSnapshotId, UUID parentEstimateId, BigDecimal baselineModelScore, Instant now) {
        this.estimateId = estimateId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.baselineSnapshotId = baselineSnapshotId;
        this.parentEstimateId = parentEstimateId;
        this.baselineModelScore = baselineModelScore;
        this.status = "pending";
        this.estimateType = "baseline_anchored_partial_update";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void processing(Instant now) {
        if ("completed".equals(status)) throw new IllegalStateException("완료된 일상 결과는 변경할 수 없습니다.");
        status = "processing";
        updatedAt = now;
    }

    public void fail(Instant now) {
        if ("completed".equals(status)) throw new IllegalStateException("완료된 일상 결과는 변경할 수 없습니다.");
        status = "failed";
        updatedAt = now;
    }

    public void complete(DailyEstimateCompletion result, Instant now) {
        if ("completed".equals(status)) throw new IllegalStateException("완료된 일상 결과는 변경할 수 없습니다.");
        status = "completed";
        estimatedModelScore = result.estimatedModelScore();
        scoreDelta = result.scoreDelta();
        modelVersion = result.modelVersion();
        thresholdVersion = result.thresholdVersion();
        riskLevel = result.riskLevel();
        resultJson = result.resultJson();
        outputFeatureSnapshot = result.outputSnapshot();
        analyzedAt = result.analyzedAt();
        updatedAt = now;
    }

    public UUID getEstimateId() { return estimateId; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getBaselineSnapshotId() { return baselineSnapshotId; }
    public UUID getParentEstimateId() { return parentEstimateId; }
    public String getStatus() { return status; }
    public String getEstimateType() { return estimateType; }
    public BigDecimal getEstimatedModelScore() { return estimatedModelScore; }
    public BigDecimal getBaselineModelScore() { return baselineModelScore; }
    public BigDecimal getScoreDelta() { return scoreDelta; }
    public String getModelVersion() { return modelVersion; }
    public String getThresholdVersion() { return thresholdVersion; }
    public String getRiskLevel() { return riskLevel; }
    public String getResultJson() { return resultJson; }
    public String getOutputFeatureSnapshot() { return outputFeatureSnapshot; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
