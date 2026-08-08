package com.neulbom.backend.game;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameResultRepository extends JpaRepository<GameResultEntity, UUID> {

    List<GameResultEntity> findAllByUserIdAndPlayedAtBetweenOrderByPlayedAtDesc(
            UUID userId, Instant from, Instant to);

    long countByUserIdAndPlayedAtBetweenAndCompletedTrue(UUID userId, Instant from, Instant to);
}
