package com.neulbom.backend.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.analysis.api.CistAiAnalysisResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiAudioUrlSigner;
import com.neulbom.backend.analysis.integration.aiserver.AiContractValidator;
import com.neulbom.backend.analysis.integration.aiserver.AiServerClient;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AdministeredQuestionResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisCreateRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisRetryItem;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisRetryRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisStatusResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.NotApplicableQuestionResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.QuestionResponseInput;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanSnapshot;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RetryItem;
import com.neulbom.backend.analysis.integration.aiserver.CistContractCatalog;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.recording.TranscriptEntity;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.session.AnswerEntity;
import com.neulbom.backend.session.AnswerRepository;
import com.neulbom.backend.session.QuestionEntity;
import com.neulbom.backend.session.QuestionRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CistAiAnalysisService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of(AiServerContracts.TIMEZONE);

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final RecordingRepository recordingRepository;
    private final TranscriptRepository transcriptRepository;
    private final CistRecognitionPlanRepository recognitionPlanRepository;
    private final CistAiAnalysisRepository analysisRepository;
    private final AiServerOperationRepository operationRepository;
    private final AiServerClient aiServerClient;
    private final AiAudioUrlSigner audioUrlSigner;
    private final AiContractValidator validator;
    private final CistContractCatalog catalog;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public CistAiAnalysisService(
            SessionRepository sessionRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            RecordingRepository recordingRepository,
            TranscriptRepository transcriptRepository,
            CistRecognitionPlanRepository recognitionPlanRepository,
            CistAiAnalysisRepository analysisRepository,
            AiServerOperationRepository operationRepository,
            AiServerClient aiServerClient,
            AiAudioUrlSigner audioUrlSigner,
            AiContractValidator validator,
            CistContractCatalog catalog,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.recordingRepository = recordingRepository;
        this.transcriptRepository = transcriptRepository;
        this.recognitionPlanRepository = recognitionPlanRepository;
        this.analysisRepository = analysisRepository;
        this.operationRepository = operationRepository;
        this.aiServerClient = aiServerClient;
        this.audioUrlSigner = audioUrlSigner;
        this.validator = validator;
        this.catalog = catalog;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public RecognitionPlanResponse createRecognitionPlan(UUID userId, UUID sessionId) {
        SessionEntity session = ownedCistSession(userId, sessionId);
        CistRecognitionPlanEntity existing = recognitionPlanRepository.findById(sessionId).orElse(null);
        if (existing != null && "completed".equals(existing.getStatus())) {
            return recognitionResponse(existing);
        }
        QuestionEntity q11 = requiredQuestion("memory_delayed_free_recall");
        AnswerEntity answer = latestAnswers(sessionId).get(q11.getId());
        if (answer == null) {
            throw validation("Q11 답변이 필요합니다.");
        }
        if (existing != null && answer.getId().equals(existing.getSubmittedResponseId())) {
            return recognitionResponse(existing);
        }
        AdministeredQuestionResponse response = administered(q11, answer);
        RecognitionPlanRequest request = new RecognitionPlanRequest(assessmentDate(session), response);
        String key = idempotencyKey("recognition-plan");
        String requestHash = hash(request);
        RecognitionPlanResponse result = aiServerClient.createRecognitionPlan(sessionId, key, request);
        validator.validateRecognitionPlan(result, sessionId);
        if ("completed".equals(result.status())) {
            session.applyCistRecognitionPlan(result.nextQuestionCodes().size());
            sessionRepository.save(session);
        }
        var now = clock.instant();
        CistRecognitionPlanEntity entity;
        int operationNumber;
        if (existing == null) {
            entity = new CistRecognitionPlanEntity(
                    sessionId,
                    result.status(),
                    key,
                    requestHash,
                    response.recordingId(),
                    response.responseId(),
                    json(result.recalledUnits()),
                    json(result.nextQuestionCodes()),
                    json(result.q11Result()),
                    result.reasonCode(),
                    Boolean.TRUE.equals(result.retryable()),
                    json(result.retryQuestionCodes()),
                    now);
            operationNumber = 0;
        } else {
            operationNumber = existing.getAttemptCount() + 1;
            existing.replace(
                    result.status(),
                    key,
                    requestHash,
                    response.recordingId(),
                    response.responseId(),
                    json(result.recalledUnits()),
                    json(result.nextQuestionCodes()),
                    json(result.q11Result()),
                    result.reasonCode(),
                    Boolean.TRUE.equals(result.retryable()),
                    json(result.retryQuestionCodes()),
                    now);
            entity = existing;
        }
        recognitionPlanRepository.save(entity);
        AiServerOperationEntity operation = new AiServerOperationEntity(
                uuidGenerator.generate(), sessionId, null, "recognition_plan", operationNumber, key, requestHash, now);
        operation.complete(result.status(), now);
        operationRepository.save(operation);
        return result;
    }

    @Transactional
    public CistAiAnalysisResponse createAnalysis(UUID userId, UUID sessionId) {
        SessionEntity session = ownedCistSession(userId, sessionId);
        CistAiAnalysisEntity existing = analysisRepository.findBySessionId(sessionId).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        if (!SessionEntity.ENDED.equals(session.getStatus())) {
            throw validation("종료된 CIST 세션만 분석할 수 있습니다.");
        }
        CistRecognitionPlanEntity plan = recognitionPlanRepository.findById(sessionId)
                .filter(value -> "completed".equals(value.getStatus()))
                .orElseThrow(() -> validation("완료된 Q11 recognition plan이 필요합니다."));
        RecognitionPlanSnapshot snapshot = new RecognitionPlanSnapshot(
                read(plan.getRecalledUnits(), AiServerContracts.MemoryUnitMap.class),
                stringList(plan.getSelectedQuestionCodes()));
        List<QuestionResponseInput> responses = buildResponses(sessionId, Set.copyOf(snapshot.selectedQuestionCodes()));
        UUID analysisId = uuidGenerator.generate();
        AnalysisCreateRequest request = new AnalysisCreateRequest(
                analysisId, sessionId, assessmentDate(session), snapshot, responses);
        validator.validateAnalysisCreate(request);
        String key = idempotencyKey("analysis-create");
        String requestHash = hash(request);
        var accepted = aiServerClient.createAnalysis(key, request);
        if (!analysisId.equals(accepted.analysisId()) || !sessionId.equals(accepted.assessmentId())) {
            throw validation("AI 서버가 다른 분석 또는 세션 식별자를 반환했습니다.");
        }
        var now = clock.instant();
        CistAiAnalysisEntity entity = new CistAiAnalysisEntity(
                analysisId,
                sessionId,
                accepted.status(),
                key,
                requestHash,
                json(responseIdentities(responses)),
                accepted.createdAt(),
                now);
        analysisRepository.save(entity);
        AiServerOperationEntity operation = new AiServerOperationEntity(
                uuidGenerator.generate(), sessionId, analysisId, "analysis_create", 0, key, requestHash, now);
        operation.complete(accepted.status(), now);
        operationRepository.save(operation);
        return toResponse(entity);
    }

    @Transactional
    public CistAiAnalysisResponse refreshAnalysis(UUID userId, UUID sessionId) {
        ownedCistSession(userId, sessionId);
        CistAiAnalysisEntity entity = analysisRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CIST AI 분석을 찾을 수 없습니다."));
        AnalysisStatusResponse result = aiServerClient.getAnalysis(entity.getAnalysisId());
        validator.validateAnalysisStatus(result, entity.getAnalysisId(), sessionId);
        var finalResult = result.result();
        entity.updateStatus(
                result.status(),
                result.retryable(),
                result.reasonCode(),
                json(result.retryItems()),
                json(finalResult),
                finalResult == null ? null : finalResult.modelScore(),
                finalResult == null ? null : finalResult.modelVersion(),
                finalResult == null ? null : finalResult.decisionThreshold(),
                finalResult == null ? null : finalResult.thresholdVersion(),
                finalResult == null ? null : finalResult.riskFlag(),
                result.updatedAt());
        analysisRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public CistAiAnalysisResponse retryAnalysis(UUID userId, UUID sessionId) {
        ownedCistSession(userId, sessionId);
        CistAiAnalysisEntity entity = analysisRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CIST AI 분석을 찾을 수 없습니다."));
        if (!"needs_retry".equals(entity.getStatus()) || !entity.isRetryable()) {
            throw validation("현재 분석은 재시도 가능한 상태가 아닙니다.");
        }
        List<RetryItem> retryItems = readList(entity.getRetryItems(), new TypeReference<>() { });
        Map<String, SubmittedResponse> previous = readMap(entity.getSubmittedResponses());
        Map<UUID, AnswerEntity> latestAnswers = latestAnswers(sessionId);
        List<AnalysisRetryItem> requestItems = new ArrayList<>();
        Map<String, SubmittedResponse> updated = new LinkedHashMap<>(previous);
        for (RetryItem retryItem : retryItems) {
            QuestionEntity question = requiredQuestion(retryItem.questionCode());
            AnswerEntity answer = latestAnswers.get(question.getId());
            if (answer == null) {
                throw validation("재시도 문항의 새 답변이 없습니다: " + retryItem.questionCode());
            }
            AdministeredQuestionResponse current = administered(question, answer);
            SubmittedResponse before = previous.get(retryItem.questionCode());
            if (before == null) {
                throw validation("기존 분석 요청의 문항 식별자를 찾을 수 없습니다.");
            }
            if ("REISSUE_AUDIO_URL".equals(retryItem.requiredAction())) {
                if (!before.recordingId().equals(current.recordingId())
                        || !before.responseId().equals(current.responseId())) {
                    throw validation("URL 재발급은 기존 녹음·응답 ID를 유지해야 합니다.");
                }
                requestItems.add(new AiServerContracts.ReissueAudioUrlItem(
                        retryItem.questionCode(), current.recordingId(), current.responseId(), current.audio()));
            } else if ("REPLACE_RESPONSE".equals(retryItem.requiredAction())) {
                if (before.recordingId().equals(current.recordingId())
                        || before.responseId().equals(current.responseId())) {
                    throw validation("응답 교체는 새로운 녹음·응답 ID를 사용해야 합니다.");
                }
                requestItems.add(new AiServerContracts.ReplaceResponseItem(
                        retryItem.questionCode(),
                        current.variantId(),
                        current.recordingId(),
                        current.responseId(),
                        current.audio(),
                        current.stt(),
                        current.timing()));
                updated.put(retryItem.questionCode(), new SubmittedResponse(current.recordingId(), current.responseId()));
            } else {
                throw validation("지원하지 않는 분석 재시도 작업입니다.");
            }
        }
        AnalysisRetryRequest request = new AnalysisRetryRequest(entity.getReasonCode(), requestItems);
        int operationNumber = entity.getRetryCount() + 1;
        String key = idempotencyKey("analysis-retry-" + operationNumber);
        String requestHash = hash(request);
        var accepted = aiServerClient.retryAnalysis(entity.getAnalysisId(), key, request);
        if (!entity.getAnalysisId().equals(accepted.analysisId()) || !sessionId.equals(accepted.assessmentId())) {
            throw validation("AI 서버 재시도 응답의 식별자가 일치하지 않습니다.");
        }
        var now = clock.instant();
        entity.recordRetry(json(updated), now);
        analysisRepository.save(entity);
        AiServerOperationEntity operation = new AiServerOperationEntity(
                uuidGenerator.generate(), sessionId, entity.getAnalysisId(), "analysis_retry",
                operationNumber, key, requestHash, now);
        operation.complete(accepted.status(), now);
        operationRepository.save(operation);
        return toResponse(entity);
    }

    private List<QuestionResponseInput> buildResponses(UUID sessionId, Set<String> selectedQuestionCodes) {
        Map<UUID, AnswerEntity> answers = latestAnswers(sessionId);
        List<QuestionResponseInput> responses = new ArrayList<>();
        for (CistContractCatalog.QuestionDefinition definition : catalog.orderedQuestions()) {
            QuestionEntity question = requiredQuestion(definition.questionCode());
            if (catalog.conditionalQuestionCodes().contains(definition.questionCode())
                    && !selectedQuestionCodes.contains(definition.questionCode())) {
                responses.add(new NotApplicableQuestionResponse(definition.questionCode(), definition.variantId()));
                continue;
            }
            AnswerEntity answer = answers.get(question.getId());
            if (answer == null) {
                throw validation("필수 문항 답변이 없습니다: " + definition.questionCode());
            }
            responses.add(administered(question, answer));
        }
        return List.copyOf(responses);
    }

    private AdministeredQuestionResponse administered(QuestionEntity question, AnswerEntity answer) {
        if (!StringUtils.hasText(question.getQuestionCode()) || !StringUtils.hasText(question.getVariantId())) {
            throw validation("문항의 AI 계약 식별자가 없습니다.");
        }
        if (answer.getRecordingId() == null) {
            throw validation("시행 문항에 녹음 ID가 없습니다: " + question.getQuestionCode());
        }
        RecordingEntity recording = recordingRepository.findById(answer.getRecordingId())
                .orElseThrow(() -> validation("답변 녹음을 찾을 수 없습니다."));
        if (!answer.getSessionId().equals(recording.getSessionId())
                || !answer.getQuestionId().equals(recording.getQuestionId())) {
            throw validation("답변과 녹음의 세션·문항 참조가 일치하지 않습니다.");
        }
        if (recording.getDurationMs() == null || recording.getDurationMs() < 1 || recording.getDurationMs() > 60_000) {
            throw validation("녹음 길이는 1~60000ms여야 합니다.");
        }
        TranscriptEntity transcript = answer.getTranscriptId() == null
                ? transcriptRepository.findByRecordingId(recording.getId())
                    .orElseThrow(() -> validation("시행 문항의 STT 결과가 없습니다."))
                : transcriptRepository.findById(answer.getTranscriptId())
                    .orElseThrow(() -> validation("시행 문항의 STT 결과를 찾을 수 없습니다."));
        if (!recording.getId().equals(transcript.getRecordingId())) {
            throw validation("STT 결과와 녹음 ID가 일치하지 않습니다.");
        }
        String rawTranscript = transcript.getTranscript();
        String sttStatus = "completed".equals(transcript.getStatus())
                ? StringUtils.hasText(rawTranscript) ? "success" : "empty_transcript"
                : "failed";
        if ("failed".equals(sttStatus)) {
            rawTranscript = null;
        }
        var result = new AdministeredQuestionResponse(
                question.getQuestionCode(),
                question.getVariantId(),
                recording.getId(),
                answer.getId(),
                audioUrlSigner.issue(recording),
                new AiServerContracts.SttInput(sttStatus, rawTranscript, null),
                new AiServerContracts.ResponseTiming(
                        answer.getResponseTimeMs() == null ? 0 : answer.getResponseTimeMs(),
                        recording.getDurationMs()));
        return result;
    }

    private Map<UUID, AnswerEntity> latestAnswers(UUID sessionId) {
        Map<UUID, AnswerEntity> latest = new HashMap<>();
        for (AnswerEntity answer : answerRepository.findAllBySessionIdOrderByAnsweredAtAsc(sessionId)) {
            latest.put(answer.getQuestionId(), answer);
        }
        return latest;
    }

    private QuestionEntity requiredQuestion(String questionCode) {
        return questionRepository.findByQuestionCodeAndActiveTrue(questionCode)
                .orElseThrow(() -> validation("백엔드에 cist-v1 문항이 없습니다: " + questionCode));
    }

    private SessionEntity ownedCistSession(UUID userId, UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("세션을 찾을 수 없습니다."));
        if (!userId.equals(session.getUserId())) {
            throw new AccessDeniedException("본인 CIST 세션만 분석할 수 있습니다.");
        }
        if (!Set.of("cist", "baseline", "onboarding").contains(session.getSessionType())) {
            throw validation("CIST 세션만 통합 AI 분석을 요청할 수 있습니다.");
        }
        return session;
    }

    private LocalDate assessmentDate(SessionEntity session) {
        return session.getStartedAt().atZone(BUSINESS_ZONE).toLocalDate();
    }

    private RecognitionPlanResponse recognitionResponse(CistRecognitionPlanEntity entity) {
        return new RecognitionPlanResponse(
                entity.getSessionId(),
                entity.getStatus(),
                AiServerContracts.QUESTION_SET_VERSION,
                AiServerContracts.WRONG_EVENT_RULE_VERSION,
                read(entity.getRecalledUnits(), AiServerContracts.MemoryUnitMap.class),
                stringList(entity.getSelectedQuestionCodes()),
                read(entity.getQ11Result(), AiServerContracts.QuestionAnalysisResult.class),
                entity.getReasonCode(),
                entity.isRetryable(),
                stringList(entity.getRetryQuestionCodes()));
    }

    private CistAiAnalysisResponse toResponse(CistAiAnalysisEntity entity) {
        return new CistAiAnalysisResponse(
                entity.getAnalysisId(),
                entity.getSessionId(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.isRetryable(),
                entity.getReasonCode(),
                tree(entity.getRetryItems()),
                tree(entity.getFinalResult()),
                entity.getModelScore(),
                entity.getModelVersion(),
                entity.getDecisionThreshold(),
                entity.getThresholdVersion(),
                entity.getRiskFlag(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private Map<String, SubmittedResponse> responseIdentities(List<QuestionResponseInput> responses) {
        Map<String, SubmittedResponse> identities = new LinkedHashMap<>();
        for (QuestionResponseInput response : responses) {
            if (response instanceof AdministeredQuestionResponse administered) {
                identities.put(response.questionCode(), new SubmittedResponse(
                        administered.recordingId(), administered.responseId()));
            }
        }
        return identities;
    }

    private Map<String, SubmittedResponse> readMap(String value) {
        return read(value, new TypeReference<>() { });
    }

    private List<String> stringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return read(value, new TypeReference<>() { });
    }

    private <T> List<T> readList(String value, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return read(value, type);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 계약 데이터를 직렬화할 수 없습니다.", exception);
        }
    }

    private JsonNode tree(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 AI 계약 데이터를 읽을 수 없습니다.", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 AI 계약 데이터를 읽을 수 없습니다.", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 AI 계약 데이터를 읽을 수 없습니다.", exception);
        }
    }

    private String hash(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("AI 요청 해시를 생성할 수 없습니다.", exception);
        }
    }

    private String idempotencyKey(String operation) {
        return operation + "-" + uuidGenerator.generate();
    }

    private ApiException validation(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CIST AI 분석 요청을 처리할 수 없습니다.", detail);
    }

    private record SubmittedResponse(UUID recordingId, UUID responseId) {
    }
}
