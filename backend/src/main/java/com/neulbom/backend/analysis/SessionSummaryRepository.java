package com.neulbom.backend.analysis;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSummaryRepository extends JpaRepository<SessionSummaryEntity, UUID> {

    Optional<SessionSummaryEntity> findBySessionId(UUID sessionId);

    List<SessionSummaryEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
