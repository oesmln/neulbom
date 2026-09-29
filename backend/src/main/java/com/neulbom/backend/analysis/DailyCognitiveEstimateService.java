package com.neulbom.backend.analysis;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyCognitiveEstimateService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private final DailyCognitiveEstimateRepository estimates;
    private final CognitiveFeatureSnapshotRepository snapshots;
    private final CognitiveFeatureSnapshotService snapshotService;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final UuidGenerator ids;
    private final Clock clock;

    public DailyCognitiveEstimateService(DailyCognitiveEstimateRepository estimates,
            CognitiveFeatureSnapshotRepository snapshots, CognitiveFeatureSnapshotService snapshotService,
            SessionRepository sessions, UserRepository users, UuidGenerator ids, Clock clock) {
        this.estimates = estimates;
        this.snapshots = snapshots;
        this.snapshotService = snapshotService;
        this.sessions = sessions;
        this.users = users;
        this.ids = ids;
        this.clock = clock;
    }

    public record CurrentSnapshot(UUID baselineSnapshotId, UUID parentEstimateId, String featureSnapshot) { }

    @Transactional(readOnly = true)
    public CurrentSnapshot findLatestCurrentSnapshot(UUID userId, UUID baselineSnapshotId) {
        CognitiveFeatureSnapshotEntity baseline = ownedBaseline(userId, baselineSnapshotId);
        return estimates.findFirstByUserIdAndBaselineSnapshotIdAndStatusOrderByUpdatedAtDescEstimateIdDesc(
                        userId, baselineSnapshotId, "completed")
                .map(estimate -> new CurrentSnapshot(baselineSnapshotId, estimate.getEstimateId(),
                        estimate.getOutputFeatureSnapshot()))
                .orElseGet(() -> new CurrentSnapshot(baselineSnapshotId, null, baseline.getFeatureSnapshot()));
    }

    @Transactional
    public DailyCognitiveEstimateEntity createDailyEstimate(UUID userId, UUID sessionId) {
        CognitiveFeatureSnapshotEntity baseline = snapshotService.findLatestBaselineSnapshot(userId)
                .orElseThrow(() -> CognitiveFeatureSnapshotService.invalid("완료된 전체 CIST 기준 스냅샷이 필요합니다."));
        return createDailyEstimate(userId, sessionId, baseline.getSnapshotId());
    }

    @Transactional
    public DailyCognitiveEstimateEntity createDailyEstimate(UUID userId, UUID sessionId, UUID baselineSnapshotId) {
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("일상 문답 사용자를 찾을 수 없습니다."));
        SessionEntity session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("일상 문답 세션을 찾을 수 없습니다."));
        if (!userId.equals(session.getUserId()) || !"emotional_qa".equals(session.getSessionType())
                || !SessionEntity.ENDED.equals(session.getStatus())) {
            throw CognitiveFeatureSnapshotService.invalid("종료된 본인의 일상 문답 세션만 분석할 수 있습니다.");
        }
        Optional<DailyCognitiveEstimateEntity> existing = estimates.findBySessionId(sessionId);
        if (existing.isPresent()) {
            DailyCognitiveEstimateEntity saved = existing.get();
            if (!baselineSnapshotId.equals(saved.getBaselineSnapshotId())) {
                throw new ApiException(HttpStatus.CONFLICT, "일상 인지 분석의 기준이 이미 정해졌습니다.",
                        "동일 세션의 기준 스냅샷은 변경할 수 없습니다.");
            }
            return saved;
        }
        CognitiveFeatureSnapshotEntity baseline = snapshots.findById(baselineSnapshotId)
                .filter(snapshot -> userId.equals(snapshot.getUserId()))
                .orElseThrow(() -> CognitiveFeatureSnapshotService.invalid("선택한 전체 CIST 기준 스냅샷이 없습니다."));
        if (estimates.existsByUserIdAndBaselineSnapshotIdAndStatusIn(userId, baseline.getSnapshotId(),
                List.of("pending", "processing"))) {
            throw new ApiException(HttpStatus.CONFLICT, "진행 중인 일상 인지 분석이 있습니다.",
                    "같은 기준 계보의 분석이 완료된 뒤 다음 분석을 시작하세요.");
        }
        CurrentSnapshot current = estimates.findCompletedBefore(
                        userId, baseline.getSnapshotId(), session.getStartedAt(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(parent -> new CurrentSnapshot(baseline.getSnapshotId(), parent.getEstimateId(),
                        parent.getOutputFeatureSnapshot()))
                .orElseGet(() -> new CurrentSnapshot(baseline.getSnapshotId(), null, baseline.getFeatureSnapshot()));
        return estimates.save(new DailyCognitiveEstimateEntity(ids.generate(), userId, sessionId,
                baseline.getSnapshotId(), current.parentEstimateId(), baseline.getBaselineModelScore(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public CurrentSnapshot findInputSnapshot(UUID estimateId) {
        DailyCognitiveEstimateEntity estimate = estimates.findById(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException("일상 인지 분석을 찾을 수 없습니다."));
        String featureSnapshot = estimate.getParentEstimateId() == null
                ? ownedBaseline(estimate.getUserId(), estimate.getBaselineSnapshotId()).getFeatureSnapshot()
                : estimates.findById(estimate.getParentEstimateId())
                    .filter(parent -> "completed".equals(parent.getStatus())
                            && estimate.getUserId().equals(parent.getUserId())
                            && estimate.getBaselineSnapshotId().equals(parent.getBaselineSnapshotId()))
                    .map(DailyCognitiveEstimateEntity::getOutputFeatureSnapshot)
                    .orElseThrow(() -> CognitiveFeatureSnapshotService.invalid("입력 특징 스냅샷을 찾을 수 없습니다."));
        return new CurrentSnapshot(estimate.getBaselineSnapshotId(), estimate.getParentEstimateId(), featureSnapshot);
    }

    @Transactional
    public DailyCognitiveEstimateEntity completeDailyEstimate(UUID estimateId, DailyEstimateCompletion completion) {
        if (completion == null) throw CognitiveFeatureSnapshotService.invalid("완료 분석 결과가 필요합니다.");
        CognitiveFeatureSnapshotService.requireScore(completion.estimatedModelScore());
        DailyCognitiveEstimateEntity estimate = estimates.findByIdForUpdate(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException("일상 인지 분석을 찾을 수 없습니다."));
        if ("completed".equals(estimate.getStatus())) {
            if (estimate.getEstimatedModelScore().compareTo(completion.estimatedModelScore()) == 0
                    && sameDecimal(estimate.getScoreDelta(), completion.scoreDelta())
                    && Objects.equals(estimate.getModelVersion(), completion.modelVersion())
                    && Objects.equals(estimate.getThresholdVersion(), completion.thresholdVersion())
                    && Objects.equals(estimate.getRiskLevel(), completion.riskLevel())
                    && completion.analyzedAt() != null
                    && estimate.getAnalyzedAt().truncatedTo(ChronoUnit.MICROS)
                            .equals(completion.analyzedAt().truncatedTo(ChronoUnit.MICROS))
                    && snapshotService.sameJson(estimate.getResultJson(), completion.resultJson())
                    && snapshotService.sameJson(estimate.getOutputFeatureSnapshot(), completion.outputSnapshot())) {
                return estimate;
            }
            throw new ApiException(HttpStatus.CONFLICT, "일상 인지 분석이 이미 완료되었습니다.",
                    "완료된 결과와 특징 스냅샷은 덮어쓸 수 없습니다.");
        }
        CognitiveFeatureSnapshotService.requireVersion(completion.modelVersion());
        CognitiveFeatureSnapshotService.requireVersion(completion.thresholdVersion());
        snapshotService.requireJsonObject(completion.resultJson());
        snapshotService.requireJsonObject(completion.outputSnapshot());
        if (completion.analyzedAt() == null || completion.analyzedAt().isBefore(estimate.getCreatedAt())) {
            throw CognitiveFeatureSnapshotService.invalid("분석 완료 시각이 올바르지 않습니다.");
        }
        estimate.complete(completion, clock.instant());
        return estimate;
    }

    @Transactional
    public DailyCognitiveEstimateEntity markProcessing(UUID estimateId) {
        DailyCognitiveEstimateEntity estimate = estimates.findByIdForUpdate(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException("일상 인지 분석을 찾을 수 없습니다."));
        if (!"completed".equals(estimate.getStatus())) estimate.processing(clock.instant());
        return estimate;
    }

    @Transactional
    public DailyCognitiveEstimateEntity markFailed(UUID estimateId) {
        DailyCognitiveEstimateEntity estimate = estimates.findByIdForUpdate(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException("일상 인지 분석을 찾을 수 없습니다."));
        if (!"completed".equals(estimate.getStatus())) estimate.fail(clock.instant());
        return estimate;
    }

    @Transactional(readOnly = true)
    public List<DailyCognitiveEstimateEntity> findDailyEstimates(UUID userId, LocalDate from, LocalDate to) {
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = to == null ? Instant.parse("9999-12-31T23:59:59Z")
                : to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant().minusNanos(1);
        return estimates.findAllByUserIdAndStatusAndAnalyzedAtBetweenOrderByAnalyzedAtAscEstimateIdAsc(
                userId, "completed", start, end);
    }

    private CognitiveFeatureSnapshotEntity ownedBaseline(UUID userId, UUID baselineSnapshotId) {
        return snapshots.findById(baselineSnapshotId)
                .filter(snapshot -> userId.equals(snapshot.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("기준 특징 스냅샷을 찾을 수 없습니다."));
    }

    private boolean sameDecimal(java.math.BigDecimal left, java.math.BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
