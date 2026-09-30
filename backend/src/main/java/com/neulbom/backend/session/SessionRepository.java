package com.neulbom.backend.session;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    List<SessionEntity> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    @Query("select session from SessionEntity session "
            + "where session.sessionType in ('cist', 'baseline', 'onboarding') "
            + "and session.status = 'ended' and session.endedAt <= :before "
            + "and session.answeredCount >= session.totalQuestions "
            + "and exists (select plan.sessionId from CistRecognitionPlanEntity plan "
            + "where plan.sessionId = session.id and plan.status = 'completed') "
            + "and not exists (select analysis.analysisId from CistAiAnalysisEntity analysis "
            + "where analysis.sessionId = session.id) "
            + "order by session.endedAt desc")
    List<SessionEntity> findEndedCistSessionsMissingAnalysis(@Param("before") Instant before, Pageable page);

    @Query("select session from SessionEntity session "
            + "where session.sessionType = 'emotional_qa' and session.status = 'ended' "
            + "and session.endedAt <= :before "
            + "and not exists (select analysis.analysisId from CistAiAnalysisEntity analysis "
            + "where analysis.sessionId = session.id) "
            + "and exists (select snapshot.snapshotId from CognitiveFeatureSnapshotEntity snapshot, "
            + "SessionEntity baseline where snapshot.sourceSessionId = baseline.id "
            + "and snapshot.userId = session.userId and baseline.endedAt <= session.startedAt "
            + "and not exists (select newer.id from SessionEntity newer "
            + "where newer.userId = session.userId "
            + "and newer.sessionType in ('cist', 'baseline', 'onboarding') "
            + "and newer.status = 'ended' and newer.endedAt <= session.startedAt "
            + "and newer.startedAt > baseline.startedAt)) "
            + "order by session.endedAt desc")
    List<SessionEntity> findEndedDailySessionsMissingAnalysis(@Param("before") Instant before, Pageable page);

    long countByUserId(UUID userId);

    @Query("select session from SessionEntity session "
            + "where session.sessionType = 'emotional_qa' and session.status = 'ended' "
            + "and session.startedAt >= :from and session.startedAt < :to "
            + "order by session.startedAt asc")
    List<SessionEntity> findEndedEmotionalQaSessionsStartedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from SessionEntity session where session.id = :id")
    java.util.Optional<SessionEntity> findByIdForUpdate(@Param("id") UUID id);
}
