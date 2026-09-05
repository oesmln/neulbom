package com.neulbom.backend.analysis;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiServerOperationRepository extends JpaRepository<AiServerOperationEntity, UUID> {
}
