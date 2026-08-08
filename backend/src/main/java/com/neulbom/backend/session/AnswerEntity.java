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
}
