package com.neulbom.backend.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "expo_push_receipts")
public class ExpoPushReceiptEntity {

    @Id
    @Column(name = "ticket_id", length = 100)
    private String ticketId;

    @Column(name = "expo_push_token", nullable = false, length = 255)
    private String expoPushToken;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(length = 20)
    private String status;

    protected ExpoPushReceiptEntity() { }

    public ExpoPushReceiptEntity(String ticketId, String expoPushToken, UUID notificationId, Instant createdAt) {
        this.ticketId = ticketId;
        this.expoPushToken = expoPushToken;
        this.notificationId = notificationId;
        this.createdAt = createdAt;
    }

    public String getTicketId() { return ticketId; }
    public String getExpoPushToken() { return expoPushToken; }
    public UUID getNotificationId() { return notificationId; }
    public Instant getCreatedAt() { return createdAt; }

    public void checked(String status, Instant checkedAt) {
        this.status = status;
        this.checkedAt = checkedAt;
    }
}
