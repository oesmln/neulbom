package com.neulbom.backend.diary;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "diary_reactions")
public class DiaryReactionEntity {

    @Id
    private UUID id;

    @Column(name = "diary_id", nullable = false)
    private UUID diaryId;

    @Column(name = "reactor_id", nullable = false)
    private UUID reactorId;

    @Column(name = "reaction_type", nullable = false, length = 20)
    private String reactionType;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
