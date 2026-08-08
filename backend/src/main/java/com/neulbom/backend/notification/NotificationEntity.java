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

    /**
     * 외부 작업 재시도나 이벤트 재전달에도 같은 수신자에게 같은 알림을
     * 다시 만들지 않기 위한 애플리케이션 이벤트 키다.
     */
    @Column(name = "event_key", length = 255)
    private String eventKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(
            UUID id,
            UUID recipientUserId,
            String title,
            String body,
            String type,
            String severity,
            String statusLabel,
            String data,
            String eventKey,
            Instant createdAt
    ) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.title = title;
        this.body = body;
        this.type = type;
        this.severity = severity;
        this.statusLabel = statusLabel;
        this.data = data;
        this.eventKey = eventKey;
        this.read = false;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getStatusLabel() { return statusLabel; }
    public String getData() { return data; }
    public String getEventKey() { return eventKey; }
    public boolean isRead() { return read; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRead(Instant readAt) {
        this.read = true;
        this.readAt = readAt;
    }
}
