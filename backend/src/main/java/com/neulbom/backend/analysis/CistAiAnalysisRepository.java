package com.neulbom.backend.analysis;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CistAiAnalysisRepository extends JpaRepository<CistAiAnalysisEntity, UUID> {
    Optional<CistAiAnalysisEntity> findBySessionId(UUID sessionId);
    List<CistAiAnalysisEntity> findAllBySessionIdIn(Collection<UUID> sessionIds);
}
