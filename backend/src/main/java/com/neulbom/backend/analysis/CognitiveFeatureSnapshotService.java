package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CognitiveFeatureSnapshotService {
    private static final Set<String> BASELINE_TYPES = Set.of("cist", "baseline", "onboarding");

    private final CognitiveFeatureSnapshotRepository snapshots;
    private final CistAiAnalysisRepository analyses;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final UuidGenerator ids;
    private final ObjectMapper json;
    private final Clock clock;

    public CognitiveFeatureSnapshotService(CognitiveFeatureSnapshotRepository snapshots,
            CistAiAnalysisRepository analyses, SessionRepository sessions, UserRepository users,
            UuidGenerator ids, ObjectMapper json, Clock clock) {
        this.snapshots = snapshots;
        this.analyses = analyses;
        this.sessions = sessions;
        this.users = users;
        this.ids = ids;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public CognitiveFeatureSnapshotEntity saveBaselineSnapshot(UUID userId, UUID sourceSessionId,
            UUID sourceAnalysisId, String questionSetVersion, String modelVersion,
            String thresholdVersion, BigDecimal baselineModelScore, String featureSnapshot) {
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("기준 검사 사용자를 찾을 수 없습니다."));
        SessionEntity session = sessions.findById(sourceSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("기준 검사 세션을 찾을 수 없습니다."));
        CistAiAnalysisEntity analysis = analyses.findById(sourceAnalysisId)
                .orElseThrow(() -> new ResourceNotFoundException("완료된 CIST 분석을 찾을 수 없습니다."));
        if (!userId.equals(session.getUserId()) || !BASELINE_TYPES.contains(session.getSessionType())
                || !sourceSessionId.equals(analysis.getSessionId()) || !"completed".equals(analysis.getStatus())
                || analysis.getModelScore() == null) {
            throw invalid("완료된 전체 CIST 분석만 기준 스냅샷으로 저장할 수 있습니다.");
        }
        requireVersion(questionSetVersion);
        requireVersion(modelVersion);
        requireVersion(thresholdVersion);
        requireScore(baselineModelScore);
        if (analysis.getModelScore().compareTo(baselineModelScore) != 0) {
            throw invalid("기준 점수와 완료된 분석 점수가 다릅니다.");
        }
        requireJsonObject(featureSnapshot);
        Optional<CognitiveFeatureSnapshotEntity> existing = snapshots.findBySourceAnalysisId(sourceAnalysisId);
        if (existing.isPresent()) {
            CognitiveFeatureSnapshotEntity saved = existing.get();
            if (!saved.getUserId().equals(userId)
                    || !saved.getSourceSessionId().equals(sourceSessionId)
                    || !saved.getQuestionSetVersion().equals(questionSetVersion)
                    || !saved.getModelVersion().equals(modelVersion)
                    || !saved.getThresholdVersion().equals(thresholdVersion)
                    || saved.getBaselineModelScore().compareTo(baselineModelScore) != 0
                    || !sameJson(saved.getFeatureSnapshot(), featureSnapshot)) {
                throw new ApiException(HttpStatus.CONFLICT, "기준 스냅샷이 이미 존재합니다.",
                        "동일 분석의 기준 스냅샷은 덮어쓸 수 없습니다.");
            }
            return saved;
        }
        return snapshots.save(new CognitiveFeatureSnapshotEntity(ids.generate(), userId, sourceSessionId,
                sourceAnalysisId, questionSetVersion, modelVersion, thresholdVersion,
                baselineModelScore, featureSnapshot, analysis.getUpdatedAt(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Optional<CognitiveFeatureSnapshotEntity> findLatestBaselineSnapshot(UUID userId) {
        return snapshots.findFirstByUserIdOrderBySourceAnalyzedAtDescSnapshotIdDesc(userId);
    }

    static void requireScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("모델 점수는 0~1이어야 합니다.");
        }
    }

    static void requireVersion(String version) {
        if (version == null || version.isBlank() || version.length() > 120) {
            throw invalid("분석 버전이 필요합니다.");
        }
    }

    void requireJsonObject(String source) {
        try {
            if (source == null || !json.readTree(source).isObject()) throw invalid("특징 스냅샷은 JSON 객체여야 합니다.");
        } catch (JsonProcessingException exception) {
            throw invalid("특징 스냅샷은 JSON 객체여야 합니다.");
        }
    }

    boolean sameJson(String left, String right) {
        try {
            return json.readTree(left).equals(json.readTree(right));
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "인지 분석 결과가 올바르지 않습니다.", detail);
    }
}
