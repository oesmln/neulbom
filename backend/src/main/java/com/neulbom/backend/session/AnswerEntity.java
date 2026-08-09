package com.neulbom.backend.session;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "answers")
public class AnswerEntity {

    public static final String SERVER_UPLOADED = "server_uploaded";

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "client_answer_id", nullable = false)
    private UUID clientAnswerId;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    @Column(name = "recording_id")
    private UUID recordingId;

    @Column(name = "transcript_id")
    private UUID transcriptId;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "sync_status", nullable = false, length = 20)
    private String syncStatus;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnswerEntity() {
    }

    public AnswerEntity(
            UUID id,
            UUID sessionId,
            UUID questionId,
            UUID clientAnswerId,
            String answerText,
            UUID recordingId,
            UUID transcriptId,
            Integer responseTimeMs,
            Instant answeredAt,
            Instant createdAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.clientAnswerId = clientAnswerId;
        this.answerText = answerText;
        this.recordingId = recordingId;
        this.transcriptId = transcriptId;
        this.responseTimeMs = responseTimeMs;
        this.syncStatus = SERVER_UPLOADED;
        this.answeredAt = answeredAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public UUID getClientAnswerId() {
        return clientAnswerId;
    }

    public String getAnswerText() {
        return answerText;
    }

    public UUID getRecordingId() {
        return recordingId;
    }

    public UUID getTranscriptId() {
        return transcriptId;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
