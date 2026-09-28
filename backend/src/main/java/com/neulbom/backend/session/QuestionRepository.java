package com.neulbom.backend.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID> {

    List<QuestionEntity> findAllByActiveTrueAndSessionTypeOrderByDisplayOrderAsc(String sessionType);

    List<QuestionEntity> findAllByActiveTrueAndSessionTypeAndQuestionTypeOrderByDisplayOrderAsc(
            String sessionType,
            String questionType);

    Optional<QuestionEntity> findByQuestionCodeAndActiveTrue(String questionCode);

    List<QuestionEntity> findAllByActiveTrueAndSessionTypeAndAdministrationModeOrderByDisplayOrderAsc(
            String sessionType,
            String administrationMode);

    Optional<QuestionEntity> findByIdAndSessionIdAndActiveTrue(UUID id, UUID sessionId);
}
