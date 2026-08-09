package com.neulbom.backend.guardian;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "guardian_invitations")
public class GuardianInvitationEntity {

    public static final String ISSUED = "issued";
    public static final String USED = "used";
    public static final String EXPIRED = "expired";
    public static final String REVOKED = "revoked";

    @Id
    private UUID id;

    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;

    @Column(name = "code_hash", nullable = false, unique = true, length = 255)
    private String codeHash;

    @Column(length = 100)
    private String relation;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "accepted_by_user_id")
    private UUID acceptedByUserId;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GuardianInvitationEntity() {
    }

    public GuardianInvitationEntity(
            UUID id,
            UUID guardianId,
            String codeHash,
            String relation,
            Instant expiresAt,
            int maxAttempts,
            Instant createdAt
    ) {
        this.id = id;
        this.guardianId = guardianId;
        this.codeHash = codeHash;
        this.relation = relation;
        this.status = ISSUED;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
        this.maxAttempts = maxAttempts;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuardianId() {
        return guardianId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public String getRelation() {
        return relation;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public UUID getAcceptedByUserId() {
        return acceptedByUserId;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isUsable(Instant now) {
        if (!ISSUED.equals(status)) {
            return false;
        }
        if (!expiresAt.isAfter(now)) {
            status = EXPIRED;
            return false;
        }
        return attemptCount < maxAttempts;
    }

    public void recordFailedAttempt() {
        attemptCount++;
        if (attemptCount >= maxAttempts) {
            status = REVOKED;
        }
    }

    public void accept(UUID elderId, Instant acceptedAt) {
        this.status = USED;
        this.acceptedByUserId = elderId;
        this.usedAt = acceptedAt;
    }
}
