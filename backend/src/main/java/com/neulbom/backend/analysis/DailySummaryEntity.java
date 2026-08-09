package com.neulbom.backend.analysis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "daily_summaries")
public class DailySummaryEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "analyzed_session_count", nullable = false)
    private int analyzedSessionCount;

    @Column(name = "analysis_status", nullable = false, length = 20)
    private String analysisStatus;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conversation_results", nullable = false, columnDefinition = "jsonb")
    private String conversationResults;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
