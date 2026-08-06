package com.neulbom.backend.session;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_type", nullable = false, length = 20)
    private String sessionType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "current_question_order", nullable = false)
    private int currentQuestionOrder;

    @Column(name = "answered_count", nullable = false)
    private int answeredCount;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String settings;

    @Column(name = "offline_mode", nullable = false)
    private boolean offlineMode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
