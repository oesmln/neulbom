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
}
