package com.neulbom.backend.analysis;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CognitiveAnalysisRepository extends JpaRepository<CognitiveAnalysisEntity, UUID> {

    List<CognitiveAnalysisEntity> findAllByUserIdOrderByAnalyzedAtDesc(UUID userId);

    List<CognitiveAnalysisEntity> findAllBySessionIdOrderByAnalyzedAtAsc(UUID sessionId);
}
