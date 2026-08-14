package com.neulbom.backend.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Short-lived server-side hand-off between provider authentication and the
 * first social account's role selection.  The raw token is never persisted;
 * only its hash is stored, just like password-reset tokens.
 */
@Entity
@Table(name = "oauth_pending_logins")
public class OAuthPendingLoginEntity {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_email", nullable = false, length = 320)
    private String providerEmail;

    @Column(name = "provider_display_name", length = 100)
    private String providerDisplayName;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OAuthPendingLoginEntity() {
    }

    public OAuthPendingLoginEntity(
            UUID id,
            String tokenHash,
            String provider,
            String providerUserId,
            String providerEmail,
            String providerDisplayName,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerEmail = providerEmail;
        this.providerDisplayName = providerDisplayName;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getProviderEmail() {
        return providerEmail;
    }

    public String getProviderDisplayName() {
        return providerDisplayName;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void markConsumed(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}
