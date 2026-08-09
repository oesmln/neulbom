package com.neulbom.backend.game;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameResultRepository extends JpaRepository<GameResultEntity, UUID> {

    Optional<GameResultEntity> findByClientGameResultId(UUID clientGameResultId);

    List<GameResultEntity> findAllByUserIdOrderByPlayedAtDesc(UUID userId);

    List<GameResultEntity> findAllByUserIdAndPlayedAtBetweenOrderByPlayedAtDesc(
            UUID userId, Instant from, Instant to);

    long countByUserIdAndPlayedAtBetweenAndCompletedTrue(UUID userId, Instant from, Instant to);
}
