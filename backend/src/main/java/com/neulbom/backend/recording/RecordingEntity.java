package com.neulbom.backend.recording;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recordings")
public class RecordingEntity {

    public static final String ANSWER = "answer";
    public static final String DIARY = "diary";
    public static final String SERVER_UPLOADED = "server_uploaded";
    public static final String PENDING = "pending";

    @Id
    private UUID id;

    @Column(name = "client_recording_id", nullable = false, unique = true)
    private UUID clientRecordingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String purpose;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_metadata", columnDefinition = "jsonb")
    private String fileMetadata;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "sync_status", nullable = false, length = 30)
    private String syncStatus;

    @Column(name = "transcript_status", nullable = false, length = 20)
    private String transcriptStatus;

    @Column(name = "analysis_status", nullable = false, length = 20)
    private String analysisStatus;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecordingEntity() {
    }

    public RecordingEntity(
            UUID id,
            UUID clientRecordingId,
            UUID userId,
            String purpose,
            UUID sessionId,
            UUID questionId,
            String storageKey,
            String originalFilename,
            String fileMetadata,
            String mimeType,
            long fileSizeBytes,
            Integer durationMs,
            Instant recordedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.clientRecordingId = clientRecordingId;
        this.userId = userId;
        this.purpose = purpose;
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.fileMetadata = fileMetadata;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.durationMs = durationMs;
        this.syncStatus = SERVER_UPLOADED;
        this.transcriptStatus = PENDING;
        this.analysisStatus = PENDING;
        this.recordedAt = recordedAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientRecordingId() {
        return clientRecordingId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPurpose() {
        return purpose;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getFileMetadata() {
        return fileMetadata;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public String getTranscriptStatus() {
        return transcriptStatus;
    }

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markTranscriptCompleted(Instant updatedAt) {
        this.transcriptStatus = "completed";
        this.updatedAt = updatedAt;
    }

    public void markAnalysisCompleted(Instant updatedAt) {
        this.analysisStatus = "completed";
        this.syncStatus = "analysis_completed";
        this.updatedAt = updatedAt;
    }
}
