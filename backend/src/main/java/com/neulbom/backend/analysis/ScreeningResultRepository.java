package com.neulbom.backend.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResultEntity, UUID> {

    Optional<ScreeningResultEntity> findBySessionId(UUID sessionId);

    List<ScreeningResultEntity> findAllByUserIdOrderByCompletedAtDesc(UUID userId);
}
