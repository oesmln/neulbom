package com.neulbom.backend.session;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "session_question_slots")
@IdClass(SessionQuestionSlotId.class)
public class SessionQuestionSlotEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Id
    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question_source", nullable = false, length = 20)
    private String questionSource;

    @Column(name = "source_question_id")
    private UUID sourceQuestionId;

    @Column(name = "question_id", unique = true)
    private UUID questionId;

    protected SessionQuestionSlotEntity() {
    }

    public SessionQuestionSlotEntity(
            UUID sessionId,
            int questionOrder,
            String questionSource,
            UUID sourceQuestionId
    ) {
        this.sessionId = sessionId;
        this.questionOrder = questionOrder;
        this.questionSource = questionSource;
        this.sourceQuestionId = sourceQuestionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getQuestionOrder() {
        return questionOrder;
    }

    public String getQuestionSource() {
        return questionSource;
    }

    public UUID getSourceQuestionId() {
        return sourceQuestionId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public void assignQuestion(UUID questionId) {
        this.questionId = questionId;
    }
}
