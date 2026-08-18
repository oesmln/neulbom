package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cist_fusion_features")
public class CistFusionFeatureEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ast_score", precision = 8, scale = 6)
    private BigDecimal astScore;

    @Column(name = "kc_electra_score", precision = 8, scale = 6)
    private BigDecimal kcElectraScore;

    @Column(name = "category_balanced_wrong_event_score", nullable = false, precision = 8, scale = 6)
    private BigDecimal categoryBalancedWrongEventScore;

    @Column(name = "category_balanced_median_delay", precision = 14, scale = 6)
    private BigDecimal categoryBalancedMedianDelay;

    @Column(name = "feature_version", nullable = false, length = 50)
    private String featureVersion;

    @Column(name = "scaler_version", length = 50)
    private String scalerVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CistFusionFeatureEntity() {
    }

    public CistFusionFeatureEntity(
            UUID id,
            UUID sessionId,
            UUID userId,
            BigDecimal astScore,
            BigDecimal kcElectraScore,
            BigDecimal categoryBalancedWrongEventScore,
            BigDecimal categoryBalancedMedianDelay,
            String featureVersion,
            String scalerVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.astScore = astScore;
        this.kcElectraScore = kcElectraScore;
        this.categoryBalancedWrongEventScore = categoryBalancedWrongEventScore;
        this.categoryBalancedMedianDelay = categoryBalancedMedianDelay;
        this.featureVersion = featureVersion;
        this.scalerVersion = scalerVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAstScore() { return astScore; }
    public BigDecimal getKcElectraScore() { return kcElectraScore; }
    public BigDecimal getCategoryBalancedWrongEventScore() { return categoryBalancedWrongEventScore; }
    public BigDecimal getCategoryBalancedMedianDelay() { return categoryBalancedMedianDelay; }
    public String getFeatureVersion() { return featureVersion; }
    public String getScalerVersion() { return scalerVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateModelScores(
            BigDecimal astScore,
            BigDecimal kcElectraScore,
            BigDecimal categoryBalancedWrongEventScore,
            BigDecimal categoryBalancedMedianDelay,
            String featureVersion,
            String scalerVersion,
            Instant updatedAt
    ) {
        if (astScore != null) {
            this.astScore = astScore;
        }
        if (kcElectraScore != null) {
            this.kcElectraScore = kcElectraScore;
        }
        this.categoryBalancedWrongEventScore = categoryBalancedWrongEventScore;
        this.categoryBalancedMedianDelay = categoryBalancedMedianDelay;
        this.featureVersion = featureVersion;
        if (scalerVersion != null) {
            this.scalerVersion = scalerVersion;
        }
        this.updatedAt = updatedAt;
    }
}
