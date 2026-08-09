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

    public static final String ACTIVE = "active";
    public static final String ENDED = "ended";

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

    protected SessionEntity() {
    }

    public SessionEntity(
            UUID id,
            UUID userId,
            String sessionType,
            int totalQuestions,
            String settings,
            boolean offlineMode,
            Instant startedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.sessionType = sessionType;
        this.status = ACTIVE;
        this.currentQuestionOrder = 1;
        this.answeredCount = 0;
        this.totalQuestions = totalQuestions;
        this.settings = settings;
        this.offlineMode = offlineMode;
        this.startedAt = startedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSessionType() {
        return sessionType;
    }

    public String getStatus() {
        return status;
    }

    public int getCurrentQuestionOrder() {
        return currentQuestionOrder;
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public String getSettings() {
        return settings;
    }

    public boolean isOfflineMode() {
        return offlineMode;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void updateSettings(String settings) {
        this.settings = settings;
    }

    public void recordAnswer() {
        if (answeredCount < totalQuestions) {
            answeredCount++;
        }
        currentQuestionOrder = Math.min(totalQuestions, answeredCount + 1);
    }

    public void end(Instant endedAt) {
        this.status = ENDED;
        this.endedAt = endedAt;
    }
}
