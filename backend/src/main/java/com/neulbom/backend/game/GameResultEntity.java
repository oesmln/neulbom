package com.neulbom.backend.game;

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
@Table(name = "game_results")
public class GameResultEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "game_type", nullable = false, length = 30)
    private String gameType;

    @Column(nullable = false)
    private int score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_times", nullable = false, columnDefinition = "jsonb")
    private String responseTimes;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "cognitive_index", precision = 8, scale = 2)
    private BigDecimal cognitiveIndex;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;
}
