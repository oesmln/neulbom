package com.neulbom.backend.diary;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryGenerationJobRepository extends JpaRepository<DiaryGenerationJobEntity, UUID> {

    Optional<DiaryGenerationJobEntity> findByUserIdAndTargetDate(UUID userId, java.time.LocalDate targetDate);

    Optional<DiaryGenerationJobEntity> findByDailySummaryId(UUID dailySummaryId);
}
