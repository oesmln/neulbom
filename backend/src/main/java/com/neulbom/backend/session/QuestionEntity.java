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
}
