package com.neulbom.backend.user;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<ConsentEntity, UUID> {

    List<ConsentEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndConsentTypeAndVersion(UUID userId, String consentType, String version);

    Optional<ConsentEntity> findFirstByUserIdAndConsentTypeOrderByCreatedAtDesc(UUID userId, String consentType);
}
