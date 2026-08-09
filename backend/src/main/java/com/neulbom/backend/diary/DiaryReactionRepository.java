package com.neulbom.backend.diary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryReactionRepository extends JpaRepository<DiaryReactionEntity, UUID> {

    List<DiaryReactionEntity> findAllByDiaryIdOrderByCreatedAtAsc(UUID diaryId);

    Optional<DiaryReactionEntity> findByDiaryIdAndReactorIdAndReactionType(UUID diaryId, UUID reactorId, String reactionType);
}
