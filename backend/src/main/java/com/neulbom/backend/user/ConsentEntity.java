package com.neulbom.backend.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consents")
public class ConsentEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "consent_type", nullable = false, length = 30)
    private String consentType;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at")
    private Instant agreedAt;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConsentEntity() {
    }

    public ConsentEntity(
            UUID id,
            UUID userId,
            String consentType,
            boolean agreed,
            Instant agreedAt,
            String version,
            Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.consentType = consentType;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
        this.version = version;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getConsentType() {
        return consentType;
    }

    public boolean isAgreed() {
        return agreed;
    }

    public Instant getAgreedAt() {
        return agreedAt;
    }

    public String getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
