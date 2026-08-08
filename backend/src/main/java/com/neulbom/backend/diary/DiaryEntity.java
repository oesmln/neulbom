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
}
