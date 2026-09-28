package com.neulbom.backend.session;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "questions")
public class QuestionEntity {

    @Id
    private UUID id;

    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType;

    @Column(name = "session_type", nullable = false, length = 20)
    private String sessionType;

    @Column(name = "question_code", unique = true, length = 80)
    private String questionCode;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "question_source", length = 20)
    private String questionSource;

    @Column(name = "source_question_id")
    private UUID sourceQuestionId;

    @Column(name = "variant_id", length = 120)
    private String variantId;

    @Column(name = "administration_mode", length = 20)
    private String administrationMode;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String hint;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "subtitle_available", nullable = false)
    private boolean subtitleAvailable;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuestionEntity() {
    }

    public QuestionEntity(
            UUID id,
            String questionType,
            String sessionType,
            String content,
            String hint,
            int displayOrder,
            boolean subtitleAvailable,
            UUID sessionId,
            String questionSource,
            UUID sourceQuestionId,
            String variantId,
            String administrationMode,
            Instant createdAt
    ) {
        this.id = id;
        this.questionType = questionType;
        this.sessionType = sessionType;
        this.content = content;
        this.hint = hint;
        this.displayOrder = displayOrder;
        this.subtitleAvailable = subtitleAvailable;
        this.active = true;
        this.sessionId = sessionId;
        this.questionSource = questionSource;
        this.sourceQuestionId = sourceQuestionId;
        this.variantId = variantId;
        this.administrationMode = administrationMode;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getSessionType() {
        return sessionType;
    }

    public String getQuestionCode() {
        return questionCode;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getQuestionSource() {
        return questionSource;
    }

    public UUID getSourceQuestionId() {
        return sourceQuestionId;
    }

    public String getVariantId() {
        return variantId;
    }

    public String getAdministrationMode() {
        return administrationMode;
    }

    public String getContent() {
        return content;
    }

    public String getHint() {
        return hint;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isSubtitleAvailable() {
        return subtitleAvailable;
    }

    public boolean isActive() {
        return active;
    }
}
