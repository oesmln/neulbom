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

    @Column(name = "client_game_result_id", nullable = false, unique = true)
    private UUID clientGameResultId;

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

    @Column(name = "matched_pairs")
    private Integer matchedPairs;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(name = "restarted_count", nullable = false)
    private int restartedCount;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "cognitive_index", precision = 8, scale = 2)
    private BigDecimal cognitiveIndex;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    protected GameResultEntity() {
    }

    public GameResultEntity(
            UUID id,
            UUID clientGameResultId,
            UUID userId,
            UUID sessionId,
            String gameType,
            int score,
            String responseTimes,
            int errorCount,
            int totalQuestions,
            Integer matchedPairs,
            Integer attemptCount,
            int durationSec,
            int restartedCount,
            boolean completed,
            BigDecimal cognitiveIndex,
            Instant playedAt
    ) {
        this.id = id;
        this.clientGameResultId = clientGameResultId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.gameType = gameType;
        this.score = score;
        this.responseTimes = responseTimes;
        this.errorCount = errorCount;
        this.totalQuestions = totalQuestions;
        this.matchedPairs = matchedPairs;
        this.attemptCount = attemptCount;
        this.durationSec = durationSec;
        this.restartedCount = restartedCount;
        this.completed = completed;
        this.cognitiveIndex = cognitiveIndex;
        this.playedAt = playedAt;
    }

    public UUID getId() { return id; }
    public UUID getClientGameResultId() { return clientGameResultId; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public String getGameType() { return gameType; }
    public int getScore() { return score; }
    public int getErrorCount() { return errorCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public Integer getMatchedPairs() { return matchedPairs; }
    public Integer getAttemptCount() { return attemptCount; }
    public int getDurationSec() { return durationSec; }
    public int getRestartedCount() { return restartedCount; }
    public boolean isCompleted() { return completed; }
    public BigDecimal getCognitiveIndex() { return cognitiveIndex; }
    public Instant getPlayedAt() { return playedAt; }
}
