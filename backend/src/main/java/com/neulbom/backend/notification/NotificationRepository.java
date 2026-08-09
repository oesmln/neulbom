package com.neulbom.backend.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    long countByRecipientUserIdAndReadFalse(UUID recipientUserId);

    List<NotificationEntity> findTop5ByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

    List<NotificationEntity> findAllByRecipientUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID recipientUserId, Instant createdAt);
}
