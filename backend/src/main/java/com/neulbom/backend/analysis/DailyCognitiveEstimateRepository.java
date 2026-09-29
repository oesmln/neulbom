package com.neulbom.backend.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyCognitiveEstimateRepository extends JpaRepository<DailyCognitiveEstimateEntity, UUID> {
    Optional<DailyCognitiveEstimateEntity> findBySessionId(UUID sessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select estimate from DailyCognitiveEstimateEntity estimate where estimate.estimateId = :id")
    Optional<DailyCognitiveEstimateEntity> findByIdForUpdate(@Param("id") UUID id);
    Optional<DailyCognitiveEstimateEntity> findFirstByUserIdAndBaselineSnapshotIdAndStatusOrderByUpdatedAtDescEstimateIdDesc(
            UUID userId, UUID baselineSnapshotId, String status);
    @Query("select estimate from DailyCognitiveEstimateEntity estimate, SessionEntity session "
            + "where estimate.sessionId = session.id and estimate.userId = :userId "
            + "and estimate.baselineSnapshotId = :baselineSnapshotId and estimate.status = 'completed' "
            + "and session.endedAt <= :before order by session.endedAt desc, estimate.estimateId desc")
    List<DailyCognitiveEstimateEntity> findCompletedBefore(
            @Param("userId") UUID userId, @Param("baselineSnapshotId") UUID baselineSnapshotId,
            @Param("before") Instant before, Pageable pageable);
    boolean existsByUserIdAndBaselineSnapshotIdAndStatusIn(UUID userId, UUID baselineSnapshotId, List<String> statuses);
    List<DailyCognitiveEstimateEntity> findAllByUserIdAndStatusAndAnalyzedAtBetweenOrderByAnalyzedAtAscEstimateIdAsc(
            UUID userId, String status, Instant from, Instant to);
}
