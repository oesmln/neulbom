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
@Table(name = "session_summaries")
public class SessionSummaryEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "vocabulary_score", precision = 8, scale = 2)
    private BigDecimal vocabularyScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keyword_flags", columnDefinition = "jsonb")
    private String keywordFlags;

    @Column(name = "qa_count", nullable = false)
    private int qaCount;

    @Column(name = "source_status", nullable = false, length = 20)
    private String sourceStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
