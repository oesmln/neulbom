package com.neulbom.backend.analysis.integration.aiserver;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisCreateRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisStatusResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.CognitiveFeatureSnapshot;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.DailyAnalysisCreateRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.DailyAnalysisResult;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.DailyAnalysisStatusResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.FusionFeatures;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.QuestionAnalysisResult;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.QuestionResponseInput;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanResponse;
import com.neulbom.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiContractValidator {

    private static final BigDecimal DECISION_THRESHOLD = new BigDecimal("0.38592870327757767");
    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("0.8061380697921943");
    private static final BigDecimal SCORE_DELTA_TOLERANCE = new BigDecimal("0.000000000001");
    private static final Set<String> CORE_CATEGORIES = Set.of(
            "orientation", "memory", "attention", "language");
    private static final Set<String> ANALYSIS_STATUSES = Set.of(
            "pending", "processing", "needs_retry", "completed", "failed");

    private final CistContractCatalog catalog;

    public AiContractValidator(CistContractCatalog catalog) {
        this.catalog = catalog;
    }

    public void validateRecognitionPlan(RecognitionPlanResponse response, java.util.UUID assessmentId) {
        require(response != null && assessmentId.equals(response.assessmentId()), "assessment_id가 세션과 일치하지 않습니다.");
        require(AiServerContracts.QUESTION_SET_VERSION.equals(response.questionSetVersion()), "question_set_version이 일치하지 않습니다.");
        require(AiServerContracts.WRONG_EVENT_RULE_VERSION.equals(response.wrongEventRuleVersion()), "wrong_event_rule_version이 일치하지 않습니다.");
        if ("completed".equals(response.status())) {
            require(response.recalledUnits() != null, "완료된 recognition plan에 recalled_units가 없습니다.");
            require(response.nextQuestionCodes() != null, "완료된 recognition plan에 next_question_codes가 없습니다.");
            require(catalog.conditionalQuestionCodes().containsAll(response.nextQuestionCodes()), "조건부 문항 코드가 올바르지 않습니다.");
            require(response.nextQuestionCodes().size() == new HashSet<>(response.nextQuestionCodes()).size(), "조건부 문항 코드가 중복되었습니다.");
            require(response.q11Result() != null, "완료된 recognition plan에 Q11 결과가 없습니다.");
        } else if ("needs_retry".equals(response.status())) {
            require(Boolean.TRUE.equals(response.retryable()), "needs_retry 응답은 retryable=true여야 합니다.");
            require(response.retryQuestionCodes() != null && !response.retryQuestionCodes().isEmpty(), "재시도 문항이 없습니다.");
        } else {
            fail("recognition plan 상태가 올바르지 않습니다.");
        }
    }

    public void validateAnalysisCreate(AnalysisCreateRequest request) {
        List<QuestionResponseInput> responses = request.responses();
        require(responses != null && responses.size() == 17, "분석 요청은 17개 문항을 포함해야 합니다.");
        Set<String> actualCodes = new HashSet<>();
        for (QuestionResponseInput response : responses) {
            require(actualCodes.add(response.questionCode()), "분석 요청에 중복 question_code가 있습니다.");
            if ("administered".equals(response.administrationStatus())) {
                require(response instanceof AiServerContracts.AdministeredQuestionResponse, "시행 문항 입력이 올바르지 않습니다.");
                validateAdministered((AiServerContracts.AdministeredQuestionResponse) response);
            } else {
                require(response instanceof AiServerContracts.NotApplicableQuestionResponse, "미시행 문항 입력이 올바르지 않습니다.");
                require(catalog.conditionalQuestionCodes().contains(response.questionCode()), "필수 문항은 not_applicable일 수 없습니다.");
            }
        }
        require(actualCodes.equals(catalog.allQuestionCodes()), "cist-v1의 17개 question_code 집합과 일치하지 않습니다.");
        Set<String> selected = Set.copyOf(request.recognitionPlan().selectedQuestionCodes());
        for (QuestionResponseInput response : responses) {
            if (catalog.conditionalQuestionCodes().contains(response.questionCode())) {
                require(selected.contains(response.questionCode()) == "administered".equals(response.administrationStatus()),
                        "recognition plan과 조건부 문항 시행 상태가 일치하지 않습니다.");
            }
        }
    }

    public void validateAnalysisStatus(AnalysisStatusResponse response, java.util.UUID analysisId, java.util.UUID assessmentId) {
        require(response != null && analysisId.equals(response.analysisId()), "analysis_id가 일치하지 않습니다.");
        require(assessmentId.equals(response.assessmentId()), "assessment_id가 세션과 일치하지 않습니다.");
        if ("completed".equals(response.status())) {
            require(response.result() != null, "completed 상태에는 최종 result가 필요합니다.");
            require(response.retryItems() == null || response.retryItems().isEmpty(), "completed 상태에는 retry_items가 없어야 합니다.");
            validateFinalResult(response.result());
            require(response.result().questionResults() != null && response.result().questionResults().size() == 17,
                    "최종 결과는 17개 문항 결과를 포함해야 합니다.");
            Set<String> administeredCodes = new HashSet<>();
            for (QuestionAnalysisResult result : response.result().questionResults()) {
                boolean administered = "administered".equals(result.administrationStatus());
                if (administered) {
                    require(administeredCodes.add(result.questionCode()), "최종 결과에 중복 question_code가 있습니다.");
                    require(result.recordingId() != null && result.responseId() != null,
                            "시행 문항에는 녹음·응답 ID가 필요합니다.");
                } else {
                    require("not_applicable".equals(result.administrationStatus()),
                            "최종 결과의 시행 상태가 올바르지 않습니다.");
                    require(result.recordingId() == null && result.responseId() == null,
                            "미시행 문항에는 녹음·응답 ID가 없어야 합니다.");
                }
            }
            if (response.result().featureSnapshot() != null) {
                require(snapshotQuestionCodes(response.result().featureSnapshot()).equals(administeredCodes),
                        "feature_snapshot의 모델 문항이 시행 문항과 일치하지 않습니다.");
            }
        } else {
            require(response.result() == null, "completed 이외 상태에는 최종 result가 없어야 합니다.");
        }
        if ("needs_retry".equals(response.status())) {
            require(response.retryable(), "needs_retry 상태는 retryable=true여야 합니다.");
            require(response.retryItems() != null && !response.retryItems().isEmpty(), "needs_retry 상태에는 retry_items가 필요합니다.");
        }
    }

    public void validateDailyAnalysisCreate(DailyAnalysisCreateRequest request) {
        require(request != null, "일상 분석 요청이 없습니다.");
        require("daily_partial_update".equals(request.analysisType()), "analysis_type이 올바르지 않습니다.");
        require(request.analysisId() != null && request.sessionId() != null && request.baselineAnalysisId() != null,
                "일상 분석 식별자가 필요합니다.");
        require(AiServerContracts.QUESTION_SET_VERSION.equals(request.questionSetVersion()),
                "question_set_version이 일치하지 않습니다.");
        require(AiServerContracts.WRONG_EVENT_RULE_VERSION.equals(request.wrongEventRuleVersion()),
                "wrong_event_rule_version이 일치하지 않습니다.");
        requireScore(request.baselineModelScore(), "baseline_model_score");
        require(request.inputSnapshot() != null, "input_snapshot이 필요합니다.");
        validateFeatureSnapshot(request.inputSnapshot(), request.inputSnapshot().modelScore());
        require(request.questionSetVersion().equals(request.inputSnapshot().questionSetVersion())
                        && request.wrongEventRuleVersion().equals(request.inputSnapshot().wrongEventRuleVersion()),
                "입력 스냅샷의 계약 버전이 일상 분석 요청과 일치하지 않습니다.");

        List<AiServerContracts.AdministeredQuestionResponse> responses = request.responses();
        require(responses != null && responses.size() == 2, "일상 분석 요청은 정확히 2개 문항이어야 합니다.");
        Set<String> codes = new HashSet<>();
        for (AiServerContracts.AdministeredQuestionResponse response : responses) {
            require(codes.add(response.questionCode()), "일상 분석 문항 코드가 중복되었습니다.");
            validateAdministered(response);
        }
        validateDailyQuestionCodes(codes);
        require(snapshotQuestionCodes(request.inputSnapshot()).containsAll(codes),
                "일상 분석 문항이 입력 스냅샷에 존재하지 않습니다.");
    }

    public void validateDailyAnalysisStatus(
            DailyAnalysisStatusResponse response,
            java.util.UUID analysisId,
            java.util.UUID sessionId,
            Set<String> requestedQuestionCodes
    ) {
        require(requestedQuestionCodes != null && requestedQuestionCodes.size() == 2,
                "요청한 일상 분석 문항은 정확히 2개여야 합니다.");
        validateDailyQuestionCodes(requestedQuestionCodes);
        require(response != null && analysisId.equals(response.analysisId()), "analysis_id가 일치하지 않습니다.");
        require(sessionId.equals(response.sessionId()), "session_id가 세션과 일치하지 않습니다.");
        require(ANALYSIS_STATUSES.contains(response.status()), "일상 분석 상태가 올바르지 않습니다.");
        require(response.createdAt() != null && response.updatedAt() != null,
                "일상 분석 상태 시각이 필요합니다.");
        require(!response.updatedAt().isBefore(response.createdAt()),
                "일상 분석 updated_at은 created_at보다 빠를 수 없습니다.");
        if ("completed".equals(response.status())) {
            require(response.result() != null, "completed 상태에는 최종 result가 필요합니다.");
            require(response.retryItems() == null || response.retryItems().isEmpty(),
                    "completed 상태에는 retry_items가 없어야 합니다.");
            validateDailyResult(response.result());
            require(new HashSet<>(response.result().updatedQuestionCodes()).equals(requestedQuestionCodes),
                    "AI 서버가 갱신한 문항이 요청한 일상 CIST 문항과 일치하지 않습니다.");
        } else {
            require(response.result() == null, "completed 이외 상태에는 최종 result가 없어야 합니다.");
        }
        if ("needs_retry".equals(response.status())) {
            require(response.retryable(), "needs_retry 상태는 retryable=true여야 합니다.");
            require(response.retryItems() != null
                            && !response.retryItems().isEmpty()
                            && response.retryItems().size() <= 2,
                    "needs_retry 상태에는 1~2개의 retry_items가 필요합니다.");
        }
    }

    private void validateFinalResult(AiServerContracts.FinalAnalysisResult result) {
        BigDecimal score = result.modelScore();
        requireScore(score, "model_score");
        require(AiServerContracts.FUSION_MODEL_VERSION.equals(result.modelVersion()),
                "model_version이 현재 Fusion 모델과 일치하지 않습니다.");
        require(result.decisionThreshold() != null
                        && result.decisionThreshold().compareTo(DECISION_THRESHOLD) == 0,
                "decision_threshold가 fusion-threshold-v2 기준과 일치하지 않습니다.");
        require(result.reviewThreshold() != null
                        && result.reviewThreshold().compareTo(REVIEW_THRESHOLD) == 0,
                "review_threshold가 fusion-threshold-v2 기준과 일치하지 않습니다.");
        require(AiServerContracts.THRESHOLD_VERSION.equals(result.thresholdVersion()),
                "threshold_version이 fusion-threshold-v2가 아닙니다.");
        require(result.riskFlag() == score.compareTo(DECISION_THRESHOLD) >= 0,
                "risk_flag가 decision_threshold 기준과 일치하지 않습니다.");
        String expectedRiskLevel = score.compareTo(DECISION_THRESHOLD) < 0
                ? "stable"
                : score.compareTo(REVIEW_THRESHOLD) < 0
                        ? "monitoring_needed"
                        : "review_needed";
        require(expectedRiskLevel.equals(result.riskLevel()),
                "risk_level이 model_score 구간과 일치하지 않습니다.");
        validateFusionFeatures(result.features());
        if (result.featureSnapshot() != null) {
            validateFeatureSnapshot(result.featureSnapshot(), score);
            require(sameFeatures(result.features(), result.featureSnapshot().fusionFeatures()),
                    "feature_snapshot의 Fusion 특징이 최종 결과와 일치하지 않습니다.");
        }
    }

    private void validateDailyResult(DailyAnalysisResult result) {
        require("daily_partial_estimate".equals(result.resultType()), "result_type이 올바르지 않습니다.");
        require(result.baselineAnalysisId() != null, "baseline_analysis_id가 필요합니다.");
        requireScore(result.baselineModelScore(), "baseline_model_score");
        requireScore(result.inputModelScore(), "input_model_score");
        requireScore(result.estimatedModelScore(), "estimated_model_score");
        require(closeTo(
                        result.scoreDeltaFromBaseline(),
                        result.estimatedModelScore().subtract(result.baselineModelScore())),
                "기준 점수 대비 변화량이 일치하지 않습니다.");
        require(closeTo(
                        result.scoreDeltaFromPrevious(),
                        result.estimatedModelScore().subtract(result.inputModelScore())),
                "직전 점수 대비 변화량이 일치하지 않습니다.");
        require(AiServerContracts.FUSION_MODEL_VERSION.equals(result.modelVersion()),
                "model_version이 현재 Fusion 모델과 일치하지 않습니다.");
        require(result.decisionThreshold() != null
                        && result.decisionThreshold().compareTo(DECISION_THRESHOLD) == 0,
                "decision_threshold가 fusion-threshold-v2 기준과 일치하지 않습니다.");
        require(result.reviewThreshold() != null
                        && result.reviewThreshold().compareTo(REVIEW_THRESHOLD) == 0,
                "review_threshold가 fusion-threshold-v2 기준과 일치하지 않습니다.");
        require(AiServerContracts.THRESHOLD_VERSION.equals(result.thresholdVersion()),
                "threshold_version이 fusion-threshold-v2가 아닙니다.");
        require(result.riskFlag() == result.estimatedModelScore().compareTo(DECISION_THRESHOLD) >= 0,
                "risk_flag가 estimated_model_score와 일치하지 않습니다.");
        String expectedRiskLevel = expectedRiskLevel(result.estimatedModelScore());
        require(expectedRiskLevel.equals(result.riskLevel()),
                "risk_level이 estimated_model_score 구간과 일치하지 않습니다.");

        require(result.updatedQuestionCodes() != null, "updated_question_codes가 필요합니다.");
        Set<String> updatedCodes = new HashSet<>(result.updatedQuestionCodes());
        require(updatedCodes.size() == 2 && result.updatedQuestionCodes().size() == 2,
                "updated_question_codes는 중복 없는 2개 문항이어야 합니다.");
        validateDailyQuestionCodes(updatedCodes);
        require(result.questionResults() != null && result.questionResults().size() == 2,
                "일상 분석 결과는 2개 문항 결과를 포함해야 합니다.");
        Set<String> resultCodes = new HashSet<>();
        result.questionResults().forEach(question -> {
            require(resultCodes.add(question.questionCode()), "일상 분석 문항 결과가 중복되었습니다.");
            require("administered".equals(question.administrationStatus()),
                    "일상 분석 문항 결과는 administered 상태여야 합니다.");
            require(question.recordingId() != null && question.responseId() != null,
                    "일상 분석 문항 결과에는 녹음·응답 ID가 필요합니다.");
        });
        require(resultCodes.equals(updatedCodes), "갱신 문항과 문항별 분석 결과가 일치하지 않습니다.");

        validateFusionFeatures(result.features());
        validateFeatureSnapshot(result.outputSnapshot(), result.estimatedModelScore());
        require(sameFeatures(result.features(), result.outputSnapshot().fusionFeatures()),
                "output_snapshot의 Fusion 특징이 일상 분석 결과와 일치하지 않습니다.");
    }

    private void validateFeatureSnapshot(CognitiveFeatureSnapshot snapshot, BigDecimal expectedScore) {
        require(snapshot != null, "인지 특징 스냅샷이 필요합니다.");
        require(AiServerContracts.FEATURE_SNAPSHOT_SCHEMA_VERSION.equals(snapshot.schemaVersion()),
                "feature snapshot schema_version이 일치하지 않습니다.");
        require(AiServerContracts.QUESTION_SET_VERSION.equals(snapshot.questionSetVersion()),
                "feature snapshot question_set_version이 일치하지 않습니다.");
        require(AiServerContracts.WRONG_EVENT_RULE_VERSION.equals(snapshot.wrongEventRuleVersion()),
                "feature snapshot wrong_event_rule_version이 일치하지 않습니다.");
        require(AiServerContracts.AST_MODEL_VERSION.equals(snapshot.astModelVersion()),
                "feature snapshot AST 모델 버전이 일치하지 않습니다.");
        require(AiServerContracts.KCELECTRA_MODEL_VERSION.equals(snapshot.kcelectraModelVersion()),
                "feature snapshot KcELECTRA 모델 버전이 일치하지 않습니다.");
        require(AiServerContracts.FUSION_MODEL_VERSION.equals(snapshot.fusionModelVersion()),
                "feature snapshot Fusion 모델 버전이 일치하지 않습니다.");
        require(AiServerContracts.THRESHOLD_VERSION.equals(snapshot.thresholdVersion()),
                "feature snapshot threshold_version이 일치하지 않습니다.");
        requireScore(snapshot.modelScore(), "feature snapshot model_score");
        require(expectedScore != null && snapshot.modelScore().compareTo(expectedScore) == 0,
                "feature snapshot model_score가 분석 결과와 일치하지 않습니다.");

        require(snapshot.astQuestionFeatures() != null && !snapshot.astQuestionFeatures().isEmpty(),
                "AST 문항 특징이 필요합니다.");
        require(snapshot.kcelectraQuestionFeatures() != null && !snapshot.kcelectraQuestionFeatures().isEmpty(),
                "KcELECTRA 문항 특징이 필요합니다.");
        Set<String> astCodes = new HashSet<>();
        Set<String> astCategories = new HashSet<>();
        snapshot.astQuestionFeatures().forEach(feature -> {
            require(astCodes.add(feature.questionCode()), "AST 문항 특징이 중복되었습니다.");
            require(feature.dementiaLogit() != null && feature.segmentCount() >= 1,
                    "AST 문항 특징이 올바르지 않습니다.");
            require(catalog.allQuestionCodes().contains(feature.questionCode()),
                    "AST 문항 코드가 cist-v1에 존재하지 않습니다.");
            require(catalog.question(feature.questionCode()).questionType().equals(feature.category()),
                    "AST 문항 범주가 cist-v1과 일치하지 않습니다.");
            astCategories.add(feature.category());
        });
        Set<String> kcelectraCodes = new HashSet<>();
        Set<String> kcelectraCategories = new HashSet<>();
        snapshot.kcelectraQuestionFeatures().forEach(feature -> {
            require(kcelectraCodes.add(feature.questionCode()), "KcELECTRA 문항 특징이 중복되었습니다.");
            require(feature.dementiaLogit() != null, "KcELECTRA 문항 logit이 필요합니다.");
            require(catalog.allQuestionCodes().contains(feature.questionCode()),
                    "KcELECTRA 문항 코드가 cist-v1에 존재하지 않습니다.");
            require(catalog.question(feature.questionCode()).questionType().equals(feature.category()),
                    "KcELECTRA 문항 범주가 cist-v1과 일치하지 않습니다.");
            kcelectraCategories.add(feature.category());
        });
        require(astCodes.equals(kcelectraCodes), "AST와 KcELECTRA 문항 특징이 일치하지 않습니다.");
        require(astCategories.equals(CORE_CATEGORIES) && kcelectraCategories.equals(CORE_CATEGORIES),
                "모델 문항 특징은 Core4 범주를 모두 포함해야 합니다.");

        require(snapshot.wrongEventObservations() != null
                        && snapshot.wrongEventObservations().size() == 17,
                "wrong_event 스냅샷은 17개 문항이어야 합니다.");
        Set<String> wrongEventCodes = new HashSet<>();
        snapshot.wrongEventObservations().forEach(observation -> {
            require(wrongEventCodes.add(observation.questionCode()), "wrong_event 문항이 중복되었습니다.");
            require(observation.wrongEvent() == null
                            || observation.wrongEvent() == 0
                            || observation.wrongEvent() == 1,
                    "wrong_event 값이 올바르지 않습니다.");
        });
        require(wrongEventCodes.equals(catalog.allQuestionCodes()),
                "wrong_event 스냅샷의 문항 구성이 cist-v1과 일치하지 않습니다.");

        require(snapshot.responseDelayObservations() != null
                        && snapshot.responseDelayObservations().size() == 17,
                "응답 지연 스냅샷은 17개 문항이어야 합니다.");
        Set<String> delayCodes = new HashSet<>();
        snapshot.responseDelayObservations().forEach(observation -> {
            require(delayCodes.add(observation.questionCode()), "응답 지연 문항이 중복되었습니다.");
            require(observation.responseDelayMs() == null || observation.responseDelayMs() >= 0,
                    "response_delay_ms가 올바르지 않습니다.");
        });
        require(delayCodes.equals(catalog.allQuestionCodes()),
                "응답 지연 스냅샷의 문항 구성이 cist-v1과 일치하지 않습니다.");
        validateFusionFeatures(snapshot.fusionFeatures());
    }

    private Set<String> snapshotQuestionCodes(CognitiveFeatureSnapshot snapshot) {
        require(snapshot != null && snapshot.astQuestionFeatures() != null,
                "feature_snapshot의 AST 문항 특징이 필요합니다.");
        return snapshot.astQuestionFeatures().stream()
                .map(AiServerContracts.AstQuestionFeatureSnapshot::questionCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void validateDailyQuestionCodes(Set<String> codes) {
        require(codes.size() == 2
                        && codes.stream().filter(AiServerContracts.DAILY_ORIENTATION_CODES::contains).count() == 1
                        && codes.stream().filter(AiServerContracts.DAILY_ATTENTION_CODES::contains).count() == 1,
                "일상 CIST 분석은 지남력 1문항과 주의력 1문항으로 구성해야 합니다.");
    }

    private void validateFusionFeatures(FusionFeatures features) {
        require(features != null
                        && features.astLogit() != null
                        && features.kcelectraLogit() != null
                        && features.categoryBalancedWrongEventScore() != null
                        && features.categoryBalancedWrongEventScore().compareTo(BigDecimal.ZERO) >= 0
                        && features.categoryBalancedWrongEventScore().compareTo(BigDecimal.ONE) <= 0
                        && features.categoryBalancedMedianDelay() != null
                        && features.categoryBalancedMedianDelay().compareTo(BigDecimal.ZERO) >= 0,
                "Fusion 특징이 올바르지 않습니다.");
    }

    private boolean sameFeatures(FusionFeatures left, FusionFeatures right) {
        return left != null
                && right != null
                && left.astLogit().compareTo(right.astLogit()) == 0
                && left.kcelectraLogit().compareTo(right.kcelectraLogit()) == 0
                && left.categoryBalancedWrongEventScore().compareTo(right.categoryBalancedWrongEventScore()) == 0
                && left.categoryBalancedMedianDelay().compareTo(right.categoryBalancedMedianDelay()) == 0;
    }

    private boolean closeTo(BigDecimal actual, BigDecimal expected) {
        return actual != null
                && actual.subtract(expected).abs().compareTo(SCORE_DELTA_TOLERANCE) <= 0;
    }

    private void requireScore(BigDecimal score, String field) {
        require(score != null
                        && score.compareTo(BigDecimal.ZERO) >= 0
                        && score.compareTo(BigDecimal.ONE) <= 0,
                field + "는 0 이상 1 이하이어야 합니다.");
    }

    private String expectedRiskLevel(BigDecimal score) {
        return score.compareTo(DECISION_THRESHOLD) < 0
                ? "stable"
                : score.compareTo(REVIEW_THRESHOLD) < 0
                        ? "monitoring_needed"
                        : "review_needed";
    }

    private void validateAdministered(AiServerContracts.AdministeredQuestionResponse response) {
        require(response.recordingId() != null && response.responseId() != null, "시행 문항에는 녹음·응답 ID가 필요합니다.");
        require(response.audio() != null && response.stt() != null && response.timing() != null, "시행 문항의 음성·STT·시간 정보가 필요합니다.");
        require(response.timing().recordingDurationMs() >= 1 && response.timing().recordingDurationMs() <= 60_000,
                "recording_duration_ms 범위가 올바르지 않습니다.");
        String status = response.stt().status();
        if ("success".equals(status)) {
            require(StringUtils.hasText(response.stt().rawTranscript()), "STT 성공 상태에는 전사문이 필요합니다.");
        } else if ("empty_transcript".equals(status)) {
            require(!StringUtils.hasText(response.stt().rawTranscript()), "empty_transcript 상태에는 전사문이 없어야 합니다.");
        } else if ("failed".equals(status)) {
            require(response.stt().rawTranscript() == null, "STT 실패 상태에는 전사문이 없어야 합니다.");
        } else {
            fail("STT 상태가 올바르지 않습니다.");
        }
    }

    private void require(boolean condition, String detail) {
        if (!condition) {
            fail(detail);
        }
    }

    private void fail(String detail) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI 분석 계약 검증에 실패했습니다.", detail);
    }
}
