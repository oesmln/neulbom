package com.neulbom.backend.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "oauth_accounts")
public class OAuthAccountEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_email", length = 320)
    private String providerEmail;

    @Column(name = "provider_display_name", length = 100)
    private String providerDisplayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OAuthAccountEntity() {
    }

    public OAuthAccountEntity(
            UUID id,
            UUID userId,
            String provider,
            String providerUserId,
            String providerEmail,
            String providerDisplayName,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerEmail = providerEmail;
        this.providerDisplayName = providerDisplayName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
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

    public void updateProfile(String providerEmail, String providerDisplayName, Instant updatedAt) {
        // Naver can omit a previously granted/available email when the user
        // declines that profile field on a later consent screen. Do not erase
        // the last known value while refreshing an already linked account.
        if (providerEmail != null && !providerEmail.isBlank()) {
            this.providerEmail = providerEmail;
        }
        if (providerDisplayName != null && !providerDisplayName.isBlank()) {
            this.providerDisplayName = providerDisplayName;
        }
        this.updatedAt = updatedAt;
    }
}
