package com.neulbom.backend.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionQuestionSlotRepository extends JpaRepository<SessionQuestionSlotEntity, SessionQuestionSlotId> {

    List<SessionQuestionSlotEntity> findAllBySessionIdOrderByQuestionOrderAsc(UUID sessionId);

    Optional<SessionQuestionSlotEntity> findBySessionIdAndQuestionOrder(UUID sessionId, int questionOrder);
}
