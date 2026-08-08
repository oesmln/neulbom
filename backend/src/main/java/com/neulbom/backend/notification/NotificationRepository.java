package com.neulbom.backend.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    long countByRecipientUserIdAndReadFalse(UUID recipientUserId);

    List<NotificationEntity> findAllByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

    List<NotificationEntity> findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(UUID recipientUserId);

    List<NotificationEntity> findAllByRecipientUserIdAndTypeOrderByCreatedAtDesc(
            UUID recipientUserId, String type);

    List<NotificationEntity> findAllByRecipientUserIdAndReadFalseAndTypeOrderByCreatedAtDesc(
            UUID recipientUserId, String type);

    Optional<NotificationEntity> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    Optional<NotificationEntity> findByRecipientUserIdAndEventKey(UUID recipientUserId, String eventKey);

    List<NotificationEntity> findTop5ByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

    List<NotificationEntity> findAllByRecipientUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID recipientUserId, Instant createdAt);
}
