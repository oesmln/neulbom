package com.neulbom.backend.game;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpLedgerRepository extends JpaRepository<XpLedgerEntity, UUID> {

    Optional<XpLedgerEntity> findByEventId(String eventId);

    List<XpLedgerEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            select coalesce(sum(entry.amount), 0)
            from XpLedgerEntity entry
            where entry.userId = :userId
              and entry.createdAt >= :from
              and entry.createdAt < :to
            """)
    long sumAmountByUserIdAndCreatedAtBetween(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
