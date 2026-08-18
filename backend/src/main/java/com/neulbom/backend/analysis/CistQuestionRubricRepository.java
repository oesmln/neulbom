package com.neulbom.backend.analysis;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CistQuestionRubricRepository extends JpaRepository<CistQuestionRubricEntity, UUID> {

    Optional<CistQuestionRubricEntity> findByQuestionIdAndActiveTrue(UUID questionId);
}
