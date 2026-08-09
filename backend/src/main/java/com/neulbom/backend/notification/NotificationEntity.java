package com.neulbom.backend.notification;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "status_label", length = 100)
    private String statusLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String data;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationEntity() {
    }

    public UUID getId() { return id; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getStatusLabel() { return statusLabel; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
}
