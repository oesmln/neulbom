package com.neulbom.backend.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CognitiveFeatureSnapshotRepository extends JpaRepository<CognitiveFeatureSnapshotEntity, UUID> {
    Optional<CognitiveFeatureSnapshotEntity> findBySourceAnalysisId(UUID sourceAnalysisId);
    Optional<CognitiveFeatureSnapshotEntity> findFirstByUserIdOrderBySourceAnalyzedAtDescSnapshotIdDesc(UUID userId);
    List<CognitiveFeatureSnapshotEntity> findAllByUserId(UUID userId);
}
