package com.neulbom.backend.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceRepository extends JpaRepository<PushDeviceEntity, String> {
    List<PushDeviceEntity> findAllByUserId(UUID userId);
}
