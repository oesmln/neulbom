package com.neulbom.backend.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CistItemEvaluationRepository extends JpaRepository<CistItemEvaluationEntity, UUID> {

    Optional<CistItemEvaluationEntity> findByAnswerIdAndEvaluatorVersion(UUID answerId, String evaluatorVersion);

    List<CistItemEvaluationEntity> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
