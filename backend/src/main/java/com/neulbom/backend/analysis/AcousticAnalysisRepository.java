package com.neulbom.backend.analysis;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcousticAnalysisRepository extends JpaRepository<AcousticAnalysisEntity, UUID> {

    Optional<AcousticAnalysisEntity> findByRecordingIdAndModelNameAndModelVersion(
            UUID recordingId, String modelName, String modelVersion);
}
