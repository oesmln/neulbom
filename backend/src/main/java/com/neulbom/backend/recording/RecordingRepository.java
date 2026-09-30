package com.neulbom.backend.recording;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordingRepository extends JpaRepository<RecordingEntity, UUID> {

    Optional<RecordingEntity> findByClientRecordingId(UUID clientRecordingId);

    List<RecordingEntity> findByUserIdAndAudioDeletedAtIsNull(UUID userId);

    List<RecordingEntity> findTop100ByAudioDeletedAtIsNullAndRecordedAtBeforeOrderByRecordedAtAsc(Instant cutoff);
}
