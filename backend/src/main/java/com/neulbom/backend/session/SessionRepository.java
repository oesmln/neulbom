package com.neulbom.backend.session;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    List<SessionEntity> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    long countByUserId(UUID userId);
}
