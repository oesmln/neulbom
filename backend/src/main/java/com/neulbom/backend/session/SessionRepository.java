package com.neulbom.backend.session;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    List<SessionEntity> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from SessionEntity session where session.id = :id")
    java.util.Optional<SessionEntity> findByIdForUpdate(@Param("id") UUID id);
}
