package com.neulbom.backend.analysis;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CistRecognitionPlanRepository extends JpaRepository<CistRecognitionPlanEntity, UUID> {
}
