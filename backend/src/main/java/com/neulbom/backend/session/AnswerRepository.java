package com.neulbom.backend.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<AnswerEntity, UUID> {

    Optional<AnswerEntity> findBySessionIdAndClientAnswerId(UUID sessionId, UUID clientAnswerId);

    boolean existsBySessionIdAndQuestionId(UUID sessionId, UUID questionId);

    Optional<AnswerEntity> findByTranscriptId(UUID transcriptId);

    List<AnswerEntity> findAllBySessionIdOrderByAnsweredAtAsc(UUID sessionId);
}
