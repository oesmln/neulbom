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
}
