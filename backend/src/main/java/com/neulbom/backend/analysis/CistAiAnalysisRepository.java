package com.neulbom.backend.analysis;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CistAiAnalysisRepository extends JpaRepository<CistAiAnalysisEntity, UUID> {
    Optional<CistAiAnalysisEntity> findBySessionId(UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select analysis from CistAiAnalysisEntity analysis where analysis.sessionId = :sessionId")
    Optional<CistAiAnalysisEntity> findBySessionIdForUpdate(@Param("sessionId") UUID sessionId);

    List<CistAiAnalysisEntity> findAllBySessionIdIn(Collection<UUID> sessionIds);
}
