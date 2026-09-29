package com.neulbom.backend.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpoPushReceiptRepository extends JpaRepository<ExpoPushReceiptEntity, String> {
    List<ExpoPushReceiptEntity> findTop100ByCheckedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(Instant before);
}
