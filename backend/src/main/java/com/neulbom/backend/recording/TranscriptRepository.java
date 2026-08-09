package com.neulbom.backend.recording;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptRepository extends JpaRepository<TranscriptEntity, UUID> {

    Optional<TranscriptEntity> findByRecordingId(UUID recordingId);
}
