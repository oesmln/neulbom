package com.neulbom.backend.analysis;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CistFusionFeatureRepository extends JpaRepository<CistFusionFeatureEntity, UUID> {

    Optional<CistFusionFeatureEntity> findBySessionId(UUID sessionId);
}
