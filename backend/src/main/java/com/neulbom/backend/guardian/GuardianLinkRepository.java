package com.neulbom.backend.guardian;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianLinkRepository extends JpaRepository<GuardianLinkEntity, UUID> {

    Optional<GuardianLinkEntity> findByGuardianIdAndElderId(UUID guardianId, UUID elderId);

    List<GuardianLinkEntity> findAllByElderIdAndStatus(UUID elderId, String status);

    List<GuardianLinkEntity> findAllByGuardianIdOrderByCreatedAtDesc(UUID guardianId);
}
