package com.neulbom.backend.analysis;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySummaryRepository extends JpaRepository<DailySummaryEntity, UUID> {

    Optional<DailySummaryEntity> findByUserIdAndLocalDateAndTimezone(UUID userId, LocalDate localDate, String timezone);

    List<DailySummaryEntity> findAllByUserIdOrderByLocalDateDesc(UUID userId);
}
