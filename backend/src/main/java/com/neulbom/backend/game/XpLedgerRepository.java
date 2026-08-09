package com.neulbom.backend.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface XpLedgerRepository extends JpaRepository<XpLedgerEntity, UUID> {

    Optional<XpLedgerEntity> findByEventId(String eventId);

    List<XpLedgerEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
