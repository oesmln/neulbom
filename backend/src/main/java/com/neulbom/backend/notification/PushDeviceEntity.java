package com.neulbom.backend.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_devices")
public class PushDeviceEntity {

    @Id
    @Column(name = "expo_push_token", length = 255)
    private String expoPushToken;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PushDeviceEntity() { }

    public PushDeviceEntity(String expoPushToken, UUID userId, String platform, Instant now) {
        this.expoPushToken = expoPushToken;
        this.userId = userId;
        this.platform = platform;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getExpoPushToken() { return expoPushToken; }
    public UUID getUserId() { return userId; }
    public String getPlatform() { return platform; }

    public void assignTo(UUID userId, String platform, Instant now) {
        this.userId = userId;
        this.platform = platform;
        this.updatedAt = now;
    }
}
