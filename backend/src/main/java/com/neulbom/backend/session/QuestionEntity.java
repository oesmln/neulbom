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
