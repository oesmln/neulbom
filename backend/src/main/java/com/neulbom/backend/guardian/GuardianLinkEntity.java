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
}
