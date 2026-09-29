package com.neulbom.backend.session;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    List<SessionEntity> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    @Query("select session from SessionEntity session "
            + "where session.sessionType = 'emotional_qa' and session.status = 'ended' "
            + "and session.startedAt >= :from and session.startedAt < :to "
            + "order by session.startedAt asc")
    List<SessionEntity> findEndedEmotionalQaSessionsStartedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from SessionEntity session where session.id = :id")
    java.util.Optional<SessionEntity> findByIdForUpdate(@Param("id") UUID id);
}
