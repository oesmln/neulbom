package com.neulbom.backend.diary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "diary_generation_jobs")
public class DiaryGenerationJobEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "daily_summary_id", unique = true)
    private UUID dailySummaryId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "diary_id")
    private UUID diaryId;

    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DiaryGenerationJobEntity() {
    }

    public DiaryGenerationJobEntity(
            UUID id,
            UUID userId,
            UUID dailySummaryId,
            LocalDate targetDate,
            String status,
            Instant scheduledAt,
            Instant availableAt,
            UUID diaryId,
            String failureReason,
            int retryCount,
            int maxRetries,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.dailySummaryId = dailySummaryId;
        this.targetDate = targetDate;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.availableAt = availableAt;
        this.diaryId = diaryId;
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getDailySummaryId() { return dailySummaryId; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getAvailableAt() { return availableAt; }
    public UUID getDiaryId() { return diaryId; }
    public String getFailureReason() { return failureReason; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
