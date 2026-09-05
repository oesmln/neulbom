package com.neulbom.backend.analysis.integration.aiserver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisCreateRequest;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.AnalysisStatusResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.QuestionAnalysisResult;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.QuestionResponseInput;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanResponse;
import com.neulbom.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiContractValidator {

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
            require(response.result().questionResults() != null && response.result().questionResults().size() == 17,
                    "최종 결과는 17개 문항 결과를 포함해야 합니다.");
            for (QuestionAnalysisResult result : response.result().questionResults()) {
                boolean administered = "administered".equals(result.administrationStatus());
                require(administered == (result.recordingId() != null && result.responseId() != null),
                        "시행 상태와 녹음·응답 ID가 일치하지 않습니다.");
            }
        } else {
            require(response.result() == null, "completed 이외 상태에는 최종 result가 없어야 합니다.");
        }
        if ("needs_retry".equals(response.status())) {
            require(response.retryable(), "needs_retry 상태는 retryable=true여야 합니다.");
            require(response.retryItems() != null && !response.retryItems().isEmpty(), "needs_retry 상태에는 retry_items가 필요합니다.");
        }
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
