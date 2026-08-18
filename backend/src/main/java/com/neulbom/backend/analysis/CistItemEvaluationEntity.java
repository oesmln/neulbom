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
@Table(name = "cist_item_evaluations")
public class CistItemEvaluationEntity {

    @Id
    private UUID id;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "evaluation_status", nullable = false, length = 20)
    private String evaluationStatus;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(precision = 8, scale = 4)
    private BigDecimal score;

    @Column(name = "max_score", precision = 8, scale = 4)
    private BigDecimal maxScore;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "explicit_wrong_event", nullable = false)
    private boolean explicitWrongEvent;

    @Column(name = "wrong_event_source", length = 30)
    private String wrongEventSource;

    @Column(name = "evaluator_version", nullable = false, length = 50)
    private String evaluatorVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CistItemEvaluationEntity() {
    }

    public CistItemEvaluationEntity(
            UUID id,
            UUID answerId,
            UUID sessionId,
            UUID questionId,
            String category,
            String evaluationStatus,
            Boolean correct,
            BigDecimal score,
            BigDecimal maxScore,
            Integer responseTimeMs,
            boolean explicitWrongEvent,
            String wrongEventSource,
            String evaluatorVersion,
            String details,
            Instant evaluatedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.answerId = answerId;
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.category = category;
        this.evaluationStatus = evaluationStatus;
        this.correct = correct;
        this.score = score;
        this.maxScore = maxScore;
        this.responseTimeMs = responseTimeMs;
        this.explicitWrongEvent = explicitWrongEvent;
        this.wrongEventSource = wrongEventSource;
        this.evaluatorVersion = evaluatorVersion;
        this.details = details;
        this.evaluatedAt = evaluatedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAnswerId() { return answerId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getQuestionId() { return questionId; }
    public String getCategory() { return category; }
    public String getEvaluationStatus() { return evaluationStatus; }
    public Boolean getCorrect() { return correct; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getMaxScore() { return maxScore; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public boolean isExplicitWrongEvent() { return explicitWrongEvent; }
    public String getWrongEventSource() { return wrongEventSource; }
    public String getEvaluatorVersion() { return evaluatorVersion; }
    public String getDetails() { return details; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
