package com.neulbom.backend.diary;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "diaries")
public class DiaryEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "recording_id")
    private UUID recordingId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "daily_summary_id", unique = true)
    private UUID dailySummaryId;

    @Column(length = 20)
    private String mood;

    @Column(name = "mood_level")
    private Integer moodLevel;

    @Column(name = "written_at", nullable = false)
    private Instant writtenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DiaryEntity() {
    }

    public DiaryEntity(
            UUID id,
            UUID userId,
            String sourceType,
            String title,
            String content,
            UUID recordingId,
            UUID sessionId,
            UUID dailySummaryId,
            String mood,
            Integer moodLevel,
            Instant writtenAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.sourceType = sourceType;
        this.title = title;
        this.content = content;
        this.recordingId = recordingId;
        this.sessionId = sessionId;
        this.dailySummaryId = dailySummaryId;
        this.mood = mood;
        this.moodLevel = moodLevel;
        this.writtenAt = writtenAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSourceType() { return sourceType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public UUID getRecordingId() { return recordingId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getDailySummaryId() { return dailySummaryId; }
    public String getMood() { return mood; }
    public Integer getMoodLevel() { return moodLevel; }
    public Instant getWrittenAt() { return writtenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String content, Instant updatedAt) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        this.updatedAt = updatedAt;
    }
}
