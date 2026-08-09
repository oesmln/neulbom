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

    protected XpLedgerEntity() {
    }

    public XpLedgerEntity(UUID id, UUID userId, String eventId, int amount, String reason, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.eventId = eventId;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getEventId() { return eventId; }
    public int getAmount() { return amount; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
