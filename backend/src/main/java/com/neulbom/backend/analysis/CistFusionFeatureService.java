package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates completed CIST item metadata into the versioned fusion feature row. */
@Service
public class CistFusionFeatureService {

    private final SessionRepository sessionRepository;
    private final CistItemEvaluationRepository evaluationRepository;
    private final CistFusionFeatureRepository featureRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public CistFusionFeatureService(
            SessionRepository sessionRepository,
            CistItemEvaluationRepository evaluationRepository,
            CistFusionFeatureRepository featureRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.evaluationRepository = evaluationRepository;
        this.featureRepository = featureRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public CistFusionFeatureEntity aggregate(
            UUID sessionId,
            BigDecimal astScore,
            BigDecimal kcElectraScore,
            String featureVersion,
            String scalerVersion
    ) {
        return aggregate(null, sessionId, astScore, kcElectraScore, featureVersion, scalerVersion);
    }

    @Transactional
    public CistFusionFeatureEntity aggregate(
            UUID userId,
            UUID sessionId,
            BigDecimal astScore,
            BigDecimal kcElectraScore,
            String featureVersion,
            String scalerVersion
    ) {
        if (featureVersion == null || featureVersion.isBlank()) {
            throw new IllegalArgumentException("featureVersion이 필요합니다.");
        }
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CIST 세션을 찾을 수 없습니다."));
        if (userId != null && !userId.equals(session.getUserId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CIST 세션 사용자와 요청 사용자가 다릅니다.", "user_id를 확인하세요.");
        }
        if (!Set.of("cist", "baseline").contains(session.getSessionType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CIST 세션이 아닙니다.", "session_id를 확인하세요.");
        }
        List<CistItemEvaluationEntity> evaluations = evaluationRepository
                .findAllBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(evaluation -> "completed".equals(evaluation.getEvaluationStatus()))
                .toList();
        if (evaluations.isEmpty()) {
            throw new IllegalStateException("완료된 CIST 문항 평가가 없습니다.");
        }

        List<CistMetadataFeatureCalculator.MetadataEvent> events = evaluations.stream()
                .map(evaluation -> new CistMetadataFeatureCalculator.MetadataEvent(
                        evaluation.getCategory(),
                        evaluation.isExplicitWrongEvent(),
                        null,
                        evaluation.getResponseTimeMs()))
                .toList();
        CistMetadataFeatureCalculator.FeatureVector vector =
                CistMetadataFeatureCalculator.calculate(events);
        java.time.Instant now = clock.instant();
        BigDecimal boundedAstScore = boundedOptional(astScore);
        BigDecimal boundedKcElectraScore = boundedOptional(kcElectraScore);
        CistFusionFeatureEntity existing = featureRepository.findBySessionId(sessionId).orElse(null);
        if (existing != null) {
            existing.updateModelScores(
                    boundedAstScore,
                    boundedKcElectraScore,
                    vector.categoryBalancedWrongEventScore(),
                    vector.categoryBalancedMedianDelay(),
                    featureVersion,
                    scalerVersion,
                    now);
            return featureRepository.save(existing);
        }
        return featureRepository.save(new CistFusionFeatureEntity(
                uuidGenerator.generate(),
                session.getId(),
                session.getUserId(),
                boundedAstScore,
                boundedKcElectraScore,
                vector.categoryBalancedWrongEventScore(),
                vector.categoryBalancedMedianDelay(),
                featureVersion,
                scalerVersion,
                now,
                now));
    }

    private BigDecimal boundedOptional(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("모델 점수는 0과 1 사이여야 합니다.");
        }
        return value;
    }
}
