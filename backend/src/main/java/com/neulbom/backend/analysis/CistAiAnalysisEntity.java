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
@Table(name = "cist_ai_analyses")
public class CistAiAnalysisEntity {

    @Id
    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "create_idempotency_key", nullable = false, unique = true, length = 200)
    private String createIdempotencyKey;

    @Column(name = "create_request_hash", nullable = false, length = 64)
    private String createRequestHash;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_items", columnDefinition = "jsonb")
    private String retryItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_responses", nullable = false, columnDefinition = "jsonb")
    private String submittedResponses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_result", columnDefinition = "jsonb")
    private String finalResult;

    @Column(name = "model_score", precision = 12, scale = 10)
    private BigDecimal modelScore;

    @Column(name = "model_version", length = 120)
    private String modelVersion;

    @Column(name = "decision_threshold", precision = 12, scale = 10)
    private BigDecimal decisionThreshold;

    @Column(name = "threshold_version", length = 120)
    private String thresholdVersion;

    @Column(name = "risk_flag")
    private Boolean riskFlag;

    @Column(name = "provider_created_at")
    private Instant providerCreatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CistAiAnalysisEntity() {
    }

    public CistAiAnalysisEntity(
            UUID analysisId,
            UUID sessionId,
            String status,
            String createIdempotencyKey,
            String createRequestHash,
            String submittedResponses,
            Instant providerCreatedAt,
            Instant now
    ) {
        this.analysisId = analysisId;
        this.sessionId = sessionId;
        this.status = status;
        this.createIdempotencyKey = createIdempotencyKey;
        this.createRequestHash = createRequestHash;
        this.submittedResponses = submittedResponses;
        this.retryCount = 0;
        this.retryable = false;
        this.providerCreatedAt = providerCreatedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateStatus(
            String status,
            boolean retryable,
            String reasonCode,
            String retryItems,
            String finalResult,
            BigDecimal modelScore,
            String modelVersion,
            BigDecimal decisionThreshold,
            String thresholdVersion,
            Boolean riskFlag,
            Instant updatedAt
    ) {
        this.status = status;
        this.retryable = retryable;
        this.reasonCode = reasonCode;
        this.retryItems = retryItems;
        this.finalResult = finalResult;
        this.modelScore = modelScore;
        this.modelVersion = modelVersion;
        this.decisionThreshold = decisionThreshold;
        this.thresholdVersion = thresholdVersion;
        this.riskFlag = riskFlag;
        this.updatedAt = updatedAt;
    }

    public void recordRetry(String submittedResponses, Instant updatedAt) {
        this.retryCount++;
        this.status = "pending";
        this.retryable = false;
        this.reasonCode = null;
        this.retryItems = null;
        this.submittedResponses = submittedResponses;
        this.updatedAt = updatedAt;
    }

    public UUID getAnalysisId() { return analysisId; }
    public UUID getSessionId() { return sessionId; }
    public String getStatus() { return status; }
    public String getCreateIdempotencyKey() { return createIdempotencyKey; }
    public String getCreateRequestHash() { return createRequestHash; }
    public int getRetryCount() { return retryCount; }
    public boolean isRetryable() { return retryable; }
    public String getReasonCode() { return reasonCode; }
    public String getRetryItems() { return retryItems; }
    public String getSubmittedResponses() { return submittedResponses; }
    public String getFinalResult() { return finalResult; }
    public BigDecimal getModelScore() { return modelScore; }
    public String getModelVersion() { return modelVersion; }
    public BigDecimal getDecisionThreshold() { return decisionThreshold; }
    public String getThresholdVersion() { return thresholdVersion; }
    public Boolean getRiskFlag() { return riskFlag; }
    public Instant getProviderCreatedAt() { return providerCreatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
