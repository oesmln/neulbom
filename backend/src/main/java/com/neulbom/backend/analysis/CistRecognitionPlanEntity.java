package com.neulbom.backend.analysis;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cist_recognition_plans")
public class CistRecognitionPlanEntity {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "submitted_recording_id", nullable = false)
    private UUID submittedRecordingId;

    @Column(name = "submitted_response_id", nullable = false)
    private UUID submittedResponseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recalled_units", columnDefinition = "jsonb")
    private String recalledUnits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_question_codes", columnDefinition = "jsonb")
    private String selectedQuestionCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "q11_result", columnDefinition = "jsonb")
    private String q11Result;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(nullable = false)
    private boolean retryable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_question_codes", columnDefinition = "jsonb")
    private String retryQuestionCodes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CistRecognitionPlanEntity() {
    }

    public CistRecognitionPlanEntity(
            UUID sessionId,
            String status,
            String idempotencyKey,
            String requestHash,
            UUID submittedRecordingId,
            UUID submittedResponseId,
            String recalledUnits,
            String selectedQuestionCodes,
            String q11Result,
            String reasonCode,
            boolean retryable,
            String retryQuestionCodes,
            Instant now
    ) {
        this.sessionId = sessionId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.attemptCount = 0;
        this.submittedRecordingId = submittedRecordingId;
        this.submittedResponseId = submittedResponseId;
        this.recalledUnits = recalledUnits;
        this.selectedQuestionCodes = selectedQuestionCodes;
        this.q11Result = q11Result;
        this.reasonCode = reasonCode;
        this.retryable = retryable;
        this.retryQuestionCodes = retryQuestionCodes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getSessionId() { return sessionId; }
    public String getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public int getAttemptCount() { return attemptCount; }
    public UUID getSubmittedRecordingId() { return submittedRecordingId; }
    public UUID getSubmittedResponseId() { return submittedResponseId; }
    public String getRecalledUnits() { return recalledUnits; }
    public String getSelectedQuestionCodes() { return selectedQuestionCodes; }
    public String getQ11Result() { return q11Result; }
    public String getReasonCode() { return reasonCode; }
    public boolean isRetryable() { return retryable; }
    public String getRetryQuestionCodes() { return retryQuestionCodes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void replace(
            String status,
            String idempotencyKey,
            String requestHash,
            UUID submittedRecordingId,
            UUID submittedResponseId,
            String recalledUnits,
            String selectedQuestionCodes,
            String q11Result,
            String reasonCode,
            boolean retryable,
            String retryQuestionCodes,
            Instant updatedAt
    ) {
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.attemptCount++;
        this.submittedRecordingId = submittedRecordingId;
        this.submittedResponseId = submittedResponseId;
        this.recalledUnits = recalledUnits;
        this.selectedQuestionCodes = selectedQuestionCodes;
        this.q11Result = q11Result;
        this.reasonCode = reasonCode;
        this.retryable = retryable;
        this.retryQuestionCodes = retryQuestionCodes;
        this.updatedAt = updatedAt;
    }
}
