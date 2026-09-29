package com.neulbom.backend.analysis.integration.aiserver;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class AiServerContractFixtures {

    private static final List<String> QUESTION_CODES = List.of(
            "orientation_year",
            "orientation_month",
            "orientation_day",
            "orientation_weekday",
            "orientation_place",
            "memory_registration_first",
            "memory_registration_second",
            "attention_digit_span_4",
            "attention_digit_span_5",
            "attention_word_reverse",
            "memory_delayed_free_recall",
            "memory_recognition_person",
            "memory_recognition_transport",
            "memory_recognition_place",
            "memory_recognition_time",
            "memory_recognition_activity",
            "language_semantic_fluency");

    private AiServerContractFixtures() {
    }

    public static AiServerContracts.CognitiveFeatureSnapshot featureSnapshot(
            List<AiServerContracts.QuestionAnalysisResult> results,
            BigDecimal modelScore,
            AiServerContracts.FusionFeatures features
    ) {
        List<AiServerContracts.QuestionAnalysisResult> administered = results.stream()
                .filter(result -> "administered".equals(result.administrationStatus()))
                .toList();

        return new AiServerContracts.CognitiveFeatureSnapshot(
                AiServerContracts.FEATURE_SNAPSHOT_SCHEMA_VERSION,
                AiServerContracts.QUESTION_SET_VERSION,
                AiServerContracts.WRONG_EVENT_RULE_VERSION,
                AiServerContracts.AST_MODEL_VERSION,
                AiServerContracts.KCELECTRA_MODEL_VERSION,
                AiServerContracts.FUSION_MODEL_VERSION,
                AiServerContracts.THRESHOLD_VERSION,
                modelScore,
                administered.stream()
                        .map(result -> new AiServerContracts.AstQuestionFeatureSnapshot(
                                result.questionCode(),
                                category(result.questionCode()),
                                new BigDecimal("0.1"),
                                1))
                        .toList(),
                administered.stream()
                        .map(result -> new AiServerContracts.KcElectraQuestionFeatureSnapshot(
                                result.questionCode(),
                                category(result.questionCode()),
                                new BigDecimal("0.2")))
                        .toList(),
                results.stream()
                        .map(result -> new AiServerContracts.WrongEventFeatureObservation(
                                result.questionCode(),
                                result.wrongEvent()))
                        .toList(),
                results.stream()
                        .map(result -> new AiServerContracts.ResponseDelayFeatureObservation(
                                result.questionCode(),
                                result.responseDelayMs()))
                        .toList(),
                features);
    }

    public static String category(String questionCode) {
        if (questionCode.startsWith("orientation_")) {
            return "orientation";
        }
        if (questionCode.startsWith("memory_")) {
            return "memory";
        }
        if (questionCode.startsWith("attention_")) {
            return "attention";
        }
        return "language";
    }

    public static List<AiServerContracts.QuestionAnalysisResult> fullQuestionResults() {
        return QUESTION_CODES.stream()
                .map(questionCode -> new AiServerContracts.QuestionAnalysisResult(
                        questionCode,
                        "administered",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "speech_detected",
                        "scored",
                        "correct",
                        0,
                        null,
                        500L,
                        null))
                .toList();
    }
}
