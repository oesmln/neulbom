package com.neulbom.backend.analysis.integration.aiserver;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class CistContractCatalog {

    private final Map<String, QuestionDefinition> questions;
    private final Set<String> alwaysRequiredQuestionCodes;
    private final Set<String> conditionalQuestionCodes;

    public CistContractCatalog(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("contracts/cist-v1.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            if (!AiServerContracts.QUESTION_SET_VERSION.equals(root.path("question_set_version").asText())) {
                throw new IllegalStateException("지원하지 않는 CIST 계약 버전입니다.");
            }
            LinkedHashMap<String, QuestionDefinition> loaded = new LinkedHashMap<>();
            for (JsonNode question : root.path("questions")) {
                QuestionDefinition definition = new QuestionDefinition(
                        question.path("question_code").asText(),
                        question.path("variant_id").asText(),
                        question.path("order").asInt(),
                        question.path("administration_mode").asText());
                if (loaded.put(definition.questionCode(), definition) != null) {
                    throw new IllegalStateException("CIST 계약에 중복 question_code가 있습니다.");
                }
            }
            if (loaded.size() != 17) {
                throw new IllegalStateException("cist-v1 계약은 정확히 17개 문항이어야 합니다.");
            }
            this.questions = Map.copyOf(loaded);
            this.alwaysRequiredQuestionCodes = readCodeSet(
                    root.path("administration").path("always_required_question_codes"));
            this.conditionalQuestionCodes = readCodeSet(
                    root.path("administration").path("conditional_question_codes"));
            if (!questions.keySet().equals(union(alwaysRequiredQuestionCodes, conditionalQuestionCodes))) {
                throw new IllegalStateException("CIST 시행 정책과 문항 코드 집합이 일치하지 않습니다.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cist-v1 계약을 읽을 수 없습니다.", exception);
        }
    }

    public List<QuestionDefinition> orderedQuestions() {
        return questions.values().stream()
                .sorted(java.util.Comparator.comparingInt(QuestionDefinition::order))
                .toList();
    }

    public Set<String> allQuestionCodes() {
        return questions.keySet();
    }

    public Set<String> alwaysRequiredQuestionCodes() {
        return alwaysRequiredQuestionCodes;
    }

    public Set<String> conditionalQuestionCodes() {
        return conditionalQuestionCodes;
    }

    public QuestionDefinition question(String questionCode) {
        QuestionDefinition definition = questions.get(questionCode);
        if (definition == null) {
            throw new IllegalArgumentException("알 수 없는 cist-v1 question_code입니다: " + questionCode);
        }
        return definition;
    }

    private Set<String> readCodeSet(JsonNode array) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        array.forEach(node -> values.add(node.asText()));
        return Set.copyOf(values);
    }

    private Set<String> union(Set<String> first, Set<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>(first);
        values.addAll(second);
        return values;
    }

    public record QuestionDefinition(
            String questionCode,
            String variantId,
            int order,
            String administrationMode
    ) {
    }
}
