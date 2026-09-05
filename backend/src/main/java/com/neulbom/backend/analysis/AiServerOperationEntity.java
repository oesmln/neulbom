package com.neulbom.backend.analysis;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_server_operations")
public class AiServerOperationEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "operation_type", nullable = false, length = 30)
    private String operationType;

    @Column(name = "operation_number", nullable = false)
    private int operationNumber;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", length = 30)
    private String responseStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiServerOperationEntity() {
    }

    public AiServerOperationEntity(
            UUID id,
            UUID sessionId,
            UUID analysisId,
            String operationType,
            int operationNumber,
            String idempotencyKey,
            String requestHash,
            Instant now
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.analysisId = analysisId;
        this.operationType = operationType;
        this.operationNumber = operationNumber;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void complete(String responseStatus, Instant updatedAt) {
        this.responseStatus = responseStatus;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getAnalysisId() { return analysisId; }
    public String getOperationType() { return operationType; }
    public int getOperationNumber() { return operationNumber; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getResponseStatus() { return responseStatus; }
}
