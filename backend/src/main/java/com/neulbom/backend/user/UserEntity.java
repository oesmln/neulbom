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

    public static final String ACTIVE = "active";
    public static final String WITHDRAWN = "withdrawn";

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

    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

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

    protected UserEntity() {
    }

    public UserEntity(
            UUID id,
            String email,
            String passwordHash,
            String name,
            String role,
            LocalDate birthDate,
            String ageGroup,
            String gender,
            String phone,
            boolean profileCompleted,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.accountStatus = ACTIVE;
        this.birthDate = birthDate;
        this.ageGroup = ageGroup;
        this.gender = gender;
        this.phone = phone;
        this.profileCompleted = profileCompleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public boolean isActive() {
        return ACTIVE.equals(accountStatus);
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void changePassword(String passwordHash, Instant changedAt) {
        this.passwordHash = passwordHash;
        this.updatedAt = changedAt;
    }

    public void withdraw(Instant withdrawnAt) {
        this.accountStatus = WITHDRAWN;
        this.withdrawnAt = withdrawnAt;
        this.email = "withdrawn-" + id + "@deleted.local";
        this.passwordHash = null;
        this.name = "탈퇴 사용자";
        this.birthDate = null;
        this.ageGroup = null;
        this.gender = null;
        this.phone = null;
        this.profileCompleted = false;
        this.updatedAt = withdrawnAt;
    }
}
