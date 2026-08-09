package com.neulbom.backend.guardian;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "report_exports")
public class ReportExportEntity {

    @Id
    private UUID id;

    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;

    @Column(name = "elder_id", nullable = false)
    private UUID elderId;

    @Column(name = "request_key", nullable = false, unique = true, length = 255)
    private String requestKey;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(nullable = false, length = 10)
    private String format;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReportExportEntity() {
    }

    public ReportExportEntity(
            UUID id,
            UUID guardianId,
            UUID elderId,
            String requestKey,
            LocalDate fromDate,
            LocalDate toDate,
            String format,
            String timezone,
            String status,
            String storageKey,
            Instant expiresAt,
            String failureReason,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.guardianId = guardianId;
        this.elderId = elderId;
        this.requestKey = requestKey;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.format = format;
        this.timezone = timezone;
        this.status = status;
        this.storageKey = storageKey;
        this.expiresAt = expiresAt;
        this.failureReason = failureReason;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getGuardianId() { return guardianId; }
    public UUID getElderId() { return elderId; }
    public String getRequestKey() { return requestKey; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public String getFormat() { return format; }
    public String getTimezone() { return timezone; }
    public String getStatus() { return status; }
    public String getStorageKey() { return storageKey; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getFailureReason() { return failureReason; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
