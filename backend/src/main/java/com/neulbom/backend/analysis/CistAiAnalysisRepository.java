package com.neulbom.backend.analysis;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CistAiAnalysisRepository extends JpaRepository<CistAiAnalysisEntity, UUID> {
    Optional<CistAiAnalysisEntity> findBySessionId(UUID sessionId);
}
