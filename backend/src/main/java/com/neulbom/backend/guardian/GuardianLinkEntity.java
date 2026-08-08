package com.neulbom.backend.guardian;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "guardian_links")
public class GuardianLinkEntity {

    public static final String PENDING = "pending";
    public static final String ACTIVE = "active";
    public static final String REVOKED = "revoked";

    @Id
    private UUID id;

    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;

    @Column(name = "elder_id", nullable = false)
    private UUID elderId;

    @Column(length = 100)
    private String relation;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GuardianLinkEntity() {
    }

    public GuardianLinkEntity(
            UUID id,
            UUID guardianId,
            UUID elderId,
            String relation,
            String status,
            boolean consentRequired,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.guardianId = guardianId;
        this.elderId = elderId;
        this.relation = relation;
        this.status = status;
        this.consentRequired = consentRequired;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuardianId() {
        return guardianId;
    }

    public UUID getElderId() {
        return elderId;
    }

    public String getRelation() {
        return relation;
    }

    public String getStatus() {
        return status;
    }

    public boolean isConsentRequired() {
        return consentRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String status, String relation, Instant updatedAt) {
        this.status = status;
        this.relation = relation;
        this.updatedAt = updatedAt;
    }

    public void updateScopesConsent(boolean consentRequired, Instant updatedAt) {
        this.consentRequired = consentRequired;
        this.updatedAt = updatedAt;
    }
}
