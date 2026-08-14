package com.neulbom.backend.user;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OAuthPendingLoginRepository extends JpaRepository<OAuthPendingLoginEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OAuthPendingLoginEntity> findByTokenHash(String tokenHash);
}
