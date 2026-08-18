package com.neulbom.backend.analysis;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.session.AnswerEntity;
import com.neulbom.backend.session.AnswerRepository;
import com.neulbom.backend.session.QuestionEntity;
import com.neulbom.backend.session.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates a CIST answer against a server-owned rubric and stores the result. */
@Service
public class CistItemEvaluationService {

    private static final String EVALUATOR_VERSION = "cist-rubric-v1";

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final CistQuestionRubricRepository rubricRepository;
    private final CistItemEvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public CistItemEvaluationService(
            AnswerRepository answerRepository,
            QuestionRepository questionRepository,
            CistQuestionRubricRepository rubricRepository,
            CistItemEvaluationRepository evaluationRepository,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.rubricRepository = rubricRepository;
        this.evaluationRepository = evaluationRepository;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public Optional<CistItemEvaluationEntity> evaluateForTranscript(UUID transcriptId, String transcript) {
        AnswerEntity answer = answerRepository.findByTranscriptId(transcriptId).orElse(null);
        if (answer == null) {
            return Optional.empty();
        }
        return evaluateAndSave(answer.getId(), transcript, null);
    }

    @Transactional
    public Optional<CistItemEvaluationEntity> evaluateAndSave(
            UUID answerId,
            String transcript,
            String annotationNote
    ) {
        AnswerEntity answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("CIST 답변을 찾을 수 없습니다."));
        QuestionEntity question = questionRepository.findById(answer.getQuestionId())
                .filter(QuestionEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("CIST 질문을 찾을 수 없습니다."));
        if (!"cist".equals(question.getSessionType())) {
            return Optional.empty();
        }

        CistQuestionRubricEntity rubric = rubricRepository
                .findByQuestionIdAndActiveTrue(question.getId())
                .orElse(null);
        if (rubric == null) {
            return Optional.empty();
        }
        Optional<CistItemEvaluationEntity> existing = evaluationRepository
                .findByAnswerIdAndEvaluatorVersion(answer.getId(), EVALUATOR_VERSION);
        if (existing.isPresent()) {
            return existing;
        }

        JsonNode expectedValues = readExpectedValues(rubric.getExpectedValues());
        CistRubricEvaluator.Result result = CistRubricEvaluator.evaluate(
                rubric.getRuleType(), expectedValues, rubric.getMaxScore(), transcript);
        boolean explicitWrong = annotationNote != null
                && CistMetadataFeatureCalculator.isExplicitWrongNote(annotationNote);
        Instant now = clock.instant();
        CistItemEvaluationEntity evaluation = new CistItemEvaluationEntity(
                uuidGenerator.generate(),
                answer.getId(),
                answer.getSessionId(),
                question.getId(),
                CistMetadataFeatureCalculator.canonicalCategory(question.getQuestionType()),
                result.status(),
                result.correct(),
                result.score(),
                rubric.getMaxScore(),
                answer.getResponseTimeMs(),
                explicitWrong,
                explicitWrong ? "manual_note" : null,
                EVALUATOR_VERSION,
                json(Map.of("rule_type", rubric.getRuleType(), "rubric_version", rubric.getRubricVersion())),
                "completed".equals(result.status()) ? now : null,
                now);
        return Optional.of(evaluationRepository.save(evaluation));
    }

    private JsonNode readExpectedValues(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CIST rubric expected_values가 올바른 JSON이 아닙니다.", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CIST 문항 평가 상세정보를 저장할 수 없습니다.", exception);
        }
    }
}
