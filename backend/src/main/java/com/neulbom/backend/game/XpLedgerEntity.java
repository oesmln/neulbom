package com.neulbom.backend.game;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "xp_ledger")
public class XpLedgerEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false, unique = true, length = 150)
    private String eventId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
