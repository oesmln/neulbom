package com.neulbom.backend.analysis.integration.aiserver;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public final class AiServerContracts {

    public static final String QUESTION_SET_VERSION = "cist-v1";
    public static final String WRONG_EVENT_RULE_VERSION = "wrong-event-v1";
    public static final String TIMEZONE = "Asia/Seoul";
    public static final String FEATURE_SNAPSHOT_SCHEMA_VERSION = "cognitive-feature-snapshot-v1";
    public static final String AST_MODEL_VERSION = "final_ast_core4_epoch6_3seed_ensemble";
    public static final String KCELECTRA_MODEL_VERSION = "final_kcelectra_service_352clips_seed_ensemble_v1";
    public static final String FUSION_MODEL_VERSION = "final_fusion_lr_21subjects_ast20_mean_logit_3seed_v2";
    public static final String THRESHOLD_VERSION = "fusion-threshold-v2";
    public static final Set<String> DAILY_ORIENTATION_CODES = Set.of(
            "orientation_year",
            "orientation_month",
            "orientation_day",
            "orientation_weekday",
            "orientation_place");
    public static final Set<String> DAILY_ATTENTION_CODES = Set.of(
            "attention_digit_span_4",
            "attention_digit_span_5",
            "attention_word_reverse");

    private AiServerContracts() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SttConfig(
            String provider,
            String apiVersion,
            String location,
            String model,
            String language,
            boolean automaticPunctuation
    ) {
        public static SttConfig googleChirp3() {
            return new SttConfig("google", "v2", "us", "chirp_3", "ko-KR", true);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AudioResource(
            URI signedUrl,
            Instant expiresAt,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SttInput(String status, String rawTranscript, String providerRequestId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ResponseTiming(
            long promptEndToRecordingStartMs,
            int recordingDurationMs,
            String clientTimingSource
    ) {
        public ResponseTiming(long promptEndToRecordingStartMs, int recordingDurationMs) {
            this(promptEndToRecordingStartMs, recordingDurationMs, "monotonic_clock");
        }
    }

    public sealed interface QuestionResponseInput
            permits AdministeredQuestionResponse, NotApplicableQuestionResponse {
        String questionCode();
        String variantId();
        String administrationStatus();
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AdministeredQuestionResponse(
            String questionCode,
            String variantId,
            String administrationStatus,
            UUID recordingId,
            UUID responseId,
            AudioResource audio,
            SttInput stt,
            ResponseTiming timing
    ) implements QuestionResponseInput {
        public AdministeredQuestionResponse(
                String questionCode,
                String variantId,
                UUID recordingId,
                UUID responseId,
                AudioResource audio,
                SttInput stt,
                ResponseTiming timing
        ) {
            this(questionCode, variantId, "administered", recordingId, responseId, audio, stt, timing);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ClipPrefetchRequest(
            String questionSetVersion,
            AdministeredQuestionResponse response
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ClipPrefetchAcceptedResponse(UUID recordingId, String status) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NotApplicableQuestionResponse(
            String questionCode,
            String variantId,
            String administrationStatus
    ) implements QuestionResponseInput {
        public NotApplicableQuestionResponse(String questionCode, String variantId) {
            this(questionCode, variantId, "not_applicable");
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MemoryUnitMap(
            boolean person,
            boolean transport,
            boolean place,
            boolean time,
            boolean activity
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionAnalysisResult(
            String questionCode,
            String administrationStatus,
            UUID recordingId,
            UUID responseId,
            String vadStatus,
            String scoringStatus,
            String answerStatus,
            Integer wrongEvent,
            String wrongEventReason,
            Long responseDelayMs,
            MemoryUnitMap recognizedMemoryUnits
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RecognitionPlanRequest(
            String questionSetVersion,
            String wrongEventRuleVersion,
            LocalDate assessmentLocalDate,
            String timezone,
            SttConfig sttConfig,
            AdministeredQuestionResponse response
    ) {
        public RecognitionPlanRequest(LocalDate date, AdministeredQuestionResponse response) {
            this(QUESTION_SET_VERSION, WRONG_EVENT_RULE_VERSION, date, TIMEZONE, SttConfig.googleChirp3(), response);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RecognitionPlanResponse(
            UUID assessmentId,
            String status,
            String questionSetVersion,
            String wrongEventRuleVersion,
            MemoryUnitMap recalledUnits,
            List<String> nextQuestionCodes,
            QuestionAnalysisResult q11Result,
            String reasonCode,
            Boolean retryable,
            List<String> retryQuestionCodes
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RecognitionPlanSnapshot(
            String sourceQuestionCode,
            MemoryUnitMap recalledUnits,
            List<String> selectedQuestionCodes
    ) {
        public RecognitionPlanSnapshot(MemoryUnitMap recalledUnits, List<String> selectedQuestionCodes) {
            this("memory_delayed_free_recall", recalledUnits, selectedQuestionCodes);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnalysisCreateRequest(
            UUID analysisId,
            UUID assessmentId,
            String questionSetVersion,
            String wrongEventRuleVersion,
            LocalDate assessmentLocalDate,
            String timezone,
            SttConfig sttConfig,
            RecognitionPlanSnapshot recognitionPlan,
            List<QuestionResponseInput> responses
    ) {
        public AnalysisCreateRequest(
                UUID analysisId,
                UUID assessmentId,
                LocalDate date,
                RecognitionPlanSnapshot recognitionPlan,
                List<QuestionResponseInput> responses
        ) {
            this(analysisId, assessmentId, QUESTION_SET_VERSION, WRONG_EVENT_RULE_VERSION, date, TIMEZONE,
                    SttConfig.googleChirp3(), recognitionPlan, responses);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnalysisAcceptedResponse(
            UUID analysisId,
            UUID assessmentId,
            String status,
            Instant createdAt
    ) {
    }

    public sealed interface AnalysisRetryItem permits ReissueAudioUrlItem, ReplaceResponseItem {
        String questionCode();
        String retryAction();
        UUID recordingId();
        UUID responseId();
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReissueAudioUrlItem(
            String questionCode,
            String retryAction,
            UUID recordingId,
            UUID responseId,
            AudioResource audio
    ) implements AnalysisRetryItem {
        public ReissueAudioUrlItem(String questionCode, UUID recordingId, UUID responseId, AudioResource audio) {
            this(questionCode, "REISSUE_AUDIO_URL", recordingId, responseId, audio);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReplaceResponseItem(
            String questionCode,
            String retryAction,
            String variantId,
            UUID recordingId,
            UUID responseId,
            AudioResource audio,
            SttInput stt,
            ResponseTiming timing
    ) implements AnalysisRetryItem {
        public ReplaceResponseItem(
                String questionCode,
                String variantId,
                UUID recordingId,
                UUID responseId,
                AudioResource audio,
                SttInput stt,
                ResponseTiming timing
        ) {
            this(questionCode, "REPLACE_RESPONSE", variantId, recordingId, responseId, audio, stt, timing);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnalysisRetryRequest(String reasonCode, List<AnalysisRetryItem> items) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RetryItem(String questionCode, String reasonCode, String requiredAction) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FusionFeatures(
            BigDecimal astLogit,
            BigDecimal kcelectraLogit,
            BigDecimal categoryBalancedWrongEventScore,
            BigDecimal categoryBalancedMedianDelay
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AstQuestionFeatureSnapshot(
            String questionCode,
            String category,
            BigDecimal dementiaLogit,
            int segmentCount
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record KcElectraQuestionFeatureSnapshot(
            String questionCode,
            String category,
            BigDecimal dementiaLogit
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WrongEventFeatureObservation(
            String questionCode,
            Integer wrongEvent
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ResponseDelayFeatureObservation(
            String questionCode,
            Long responseDelayMs
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CognitiveFeatureSnapshot(
            String schemaVersion,
            String questionSetVersion,
            String wrongEventRuleVersion,
            String astModelVersion,
            String kcelectraModelVersion,
            String fusionModelVersion,
            String thresholdVersion,
            BigDecimal modelScore,
            List<AstQuestionFeatureSnapshot> astQuestionFeatures,
            List<KcElectraQuestionFeatureSnapshot> kcelectraQuestionFeatures,
            List<WrongEventFeatureObservation> wrongEventObservations,
            List<ResponseDelayFeatureObservation> responseDelayObservations,
            FusionFeatures fusionFeatures
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FinalAnalysisResult(
            String questionSetVersion,
            String wrongEventRuleVersion,
            String modelVersion,
            BigDecimal modelScore,
            BigDecimal decisionThreshold,
            BigDecimal reviewThreshold,
            String thresholdVersion,
            boolean riskFlag,
            String riskLevel,
            FusionFeatures features,
            CognitiveFeatureSnapshot featureSnapshot,
            List<QuestionAnalysisResult> questionResults
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnalysisStatusResponse(
            UUID analysisId,
            UUID assessmentId,
            String status,
            Instant createdAt,
            Instant updatedAt,
            boolean retryable,
            String reasonCode,
            List<RetryItem> retryItems,
            FinalAnalysisResult result
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DailyAnalysisCreateRequest(
            String analysisType,
            UUID analysisId,
            UUID sessionId,
            UUID baselineAnalysisId,
            String questionSetVersion,
            String wrongEventRuleVersion,
            LocalDate assessmentLocalDate,
            String timezone,
            SttConfig sttConfig,
            BigDecimal baselineModelScore,
            CognitiveFeatureSnapshot inputSnapshot,
            List<AdministeredQuestionResponse> responses
    ) {
        public DailyAnalysisCreateRequest(
                UUID analysisId,
                UUID sessionId,
                UUID baselineAnalysisId,
                LocalDate assessmentLocalDate,
                BigDecimal baselineModelScore,
                CognitiveFeatureSnapshot inputSnapshot,
                List<AdministeredQuestionResponse> responses
        ) {
            this(
                    "daily_partial_update",
                    analysisId,
                    sessionId,
                    baselineAnalysisId,
                    QUESTION_SET_VERSION,
                    WRONG_EVENT_RULE_VERSION,
                    assessmentLocalDate,
                    TIMEZONE,
                    SttConfig.googleChirp3(),
                    baselineModelScore,
                    inputSnapshot,
                    responses);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DailyAnalysisAcceptedResponse(
            UUID analysisId,
            UUID sessionId,
            String status,
            Instant createdAt
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DailyAnalysisResult(
            String resultType,
            UUID baselineAnalysisId,
            BigDecimal baselineModelScore,
            BigDecimal inputModelScore,
            BigDecimal estimatedModelScore,
            BigDecimal scoreDeltaFromBaseline,
            BigDecimal scoreDeltaFromPrevious,
            String modelVersion,
            BigDecimal decisionThreshold,
            BigDecimal reviewThreshold,
            String thresholdVersion,
            boolean riskFlag,
            String riskLevel,
            List<String> updatedQuestionCodes,
            FusionFeatures features,
            CognitiveFeatureSnapshot outputSnapshot,
            List<QuestionAnalysisResult> questionResults
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DailyAnalysisStatusResponse(
            UUID analysisId,
            UUID sessionId,
            String status,
            Instant createdAt,
            Instant updatedAt,
            boolean retryable,
            String reasonCode,
            List<RetryItem> retryItems,
            DailyAnalysisResult result
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ErrorResponse(ErrorObject error) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ErrorObject(String code, String message, boolean retryable, Map<String, Object> details) {
    }
}
