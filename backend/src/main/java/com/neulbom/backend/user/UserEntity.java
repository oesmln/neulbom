package com.neulbom.backend.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "age_group", length = 20)
    private String ageGroup;

    @Column(length = 20)
    private String gender;

    @Column(length = 30)
    private String phone;

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
