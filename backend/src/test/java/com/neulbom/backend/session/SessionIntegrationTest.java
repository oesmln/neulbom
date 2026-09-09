package com.neulbom.backend.session;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.guardian.GuardianLinkEntity;
import com.neulbom.backend.guardian.GuardianLinkRepository;
import com.neulbom.backend.guardian.GuardianLinkScopeEntity;
import com.neulbom.backend.guardian.GuardianLinkScopeRepository;
import com.neulbom.backend.recording.RecordingEntity;
import com.neulbom.backend.recording.RecordingRepository;
import com.neulbom.backend.recording.TranscriptEntity;
import com.neulbom.backend.recording.TranscriptRepository;
import com.neulbom.backend.user.ConsentEntity;
import com.neulbom.backend.user.ConsentRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionIntegrationTest {

    private static final UUID ORIENTATION_QUESTION = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuardianLinkRepository guardianLinkRepository;

    @Autowired
    private GuardianLinkScopeRepository guardianLinkScopeRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Autowired
    private UuidGenerator uuidGenerator;

    @Test
    void elderCanResumeSessionSaveIdempotentAnswersAndEndOnce() throws Exception {
        UserEntity elder = saveUser("session-owner", "elder");

        String sessionBody = mockMvc.perform(post("/api/v1/sessions")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "%s",
                                  "session_type": "cist",
                                  "preferred_hearing_side": "right",
                                  "subtitle_enabled": true,
                                  "offline_mode": true
                                }
                                """.formatted(elder.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.session_type").value("cist"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.total_questions").value(17))
                .andExpect(jsonPath("$.settings.preferred_hearing_side").value("right"))
                .andExpect(jsonPath("$.recording_sync_status").value("device_saved"))
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(sessionBody).get("session_id").asText());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", sessionId)
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_question_order").value(1));

        String answeredAt = Instant.now().minusSeconds(1).toString();
        String answerJson = """
                {
                  "client_answer_id": "%s",
                  "question_id": "%s",
                  "answer_text": "오늘은 월요일입니다.",
                  "response_time_ms": 1200,
                  "answered_at": "%s"
                }
                """.formatted(UUID.randomUUID(), ORIENTATION_QUESTION, answeredAt);

        String firstAnswerBody = mockMvc.perform(post("/api/v1/sessions/{sessionId}/answers", sessionId)
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.next_question_order").value(2))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/answers", sessionId)
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.next_question_order").value(2));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/answers", sessionId)
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.length()").value(1))
                .andExpect(jsonPath("$.answers[0].question_text").value("올해는 몇 년도입니까?"));

        mockMvc.perform(patch("/api/v1/sessions/{sessionId}/settings", sessionId)
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speech_rate\":1.1,\"sound_effect_enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.speech_rate").value(1.1))
                .andExpect(jsonPath("$.settings.sound_effect_enabled").value(true));

        mockMvc.perform(patch("/api/v1/sessions/{sessionId}/end", sessionId)
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.analysis_status").value("pending"))
                .andExpect(jsonPath("$.xp_earned").value(30))
                .andExpect(jsonPath("$.character_level").value(1));

        mockMvc.perform(patch("/api/v1/sessions/{sessionId}/end", sessionId)
                .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.xp_earned").value(0));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/answers", sessionId)
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/sessions")
                        .with(jwtFor(elder))
                        .param("user_id", elder.getId().toString())
                        .param("session_type", "cist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.sessions[0].session_id").value(sessionId.toString()));

        org.assertj.core.api.Assertions.assertThat(firstAnswerBody).contains("answer_id");
    }

    @Test
    void guardianCanReadScopedSessionButCannotMutateOrCrossUser() throws Exception {
        UserEntity guardian = saveUser("session-guardian", "guardian");
        UserEntity elder = saveUser("session-elder", "elder");
        UserEntity otherElder = saveUser("session-other-elder", "elder");
        Instant now = Instant.now();
        GuardianLinkEntity link = guardianLinkRepository.save(new GuardianLinkEntity(
                uuidGenerator.generate(),
                guardian.getId(),
                elder.getId(),
                "보호자",
                GuardianLinkEntity.ACTIVE,
                false,
                now,
                now));
        guardianLinkScopeRepository.save(new GuardianLinkScopeEntity(link.getId(), "screening"));
        String consentVersion = "session-v1-" + UUID.randomUUID().toString().substring(0, 8);
        consentRepository.save(new ConsentEntity(
                uuidGenerator.generate(),
                elder.getId(),
                "guardian_access",
                true,
                now,
                consentVersion,
                now));

        String sessionBody = mockMvc.perform(post("/api/v1/sessions")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + elder.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(sessionBody).get("session_id").asText());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", sessionId)
                        .with(jwtFor(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(elder.getId().toString()));

        mockMvc.perform(patch("/api/v1/sessions/{sessionId}/settings", sessionId)
                        .with(jwtFor(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subtitle_enabled\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/sessions")
                        .with(jwtFor(guardian))
                        .param("user_id", otherElder.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void answerRejectsForeignOrMismatchedRecordingReferences() throws Exception {
        UserEntity elder = saveUser("answer-reference-owner", "elder");
        UserEntity other = saveUser("answer-reference-other", "elder");
        UUID sessionId = startCistSession(elder);
        Instant now = Instant.now().minusSeconds(1);

        RecordingEntity foreignRecording = saveRecording(
                other.getId(), sessionId, ORIENTATION_QUESTION, now);
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/answers", sessionId)
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(ORIENTATION_QUESTION, foreignRecording.getId(), null, now)))
                .andExpect(status().isForbidden());

        UUID anotherQuestionId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        RecordingEntity wrongQuestionRecording = saveRecording(
                elder.getId(), sessionId, anotherQuestionId, now);
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/answers", sessionId)
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(ORIENTATION_QUESTION, wrongQuestionRecording.getId(), null, now)))
                .andExpect(status().isUnprocessableEntity());

        RecordingEntity validRecording = saveRecording(
                elder.getId(), sessionId, ORIENTATION_QUESTION, now);
        UUID mismatchedTranscriptId = UUID.randomUUID();
        transcriptRepository.save(new TranscriptEntity(
                mismatchedTranscriptId,
                wrongQuestionRecording.getId(),
                "다른 문항 전사문",
                new BigDecimal("1.000"),
                new BigDecimal("0.90"),
                "ko-KR",
                "test",
                "v1",
                "completed",
                now,
                now,
                now));
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/answers", sessionId)
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(
                                ORIENTATION_QUESTION,
                                validRecording.getId(),
                                mismatchedTranscriptId,
                                now)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void questionsAreFilteredBySessionTypeAndOwner() throws Exception {
        UserEntity elder = saveUser("question-owner", "elder");

        mockMvc.perform(get("/api/v1/questions/daily")
                        .with(jwtFor(elder))
                        .param("user_id", elder.getId().toString())
                        .param("session_type", "cist")
                        .param("type", "memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(8))
                .andExpect(jsonPath("$.questions[0].type").value("memory"))
                .andExpect(jsonPath("$.questions[0].question_code").isNotEmpty())
                .andExpect(jsonPath("$.questions[0].variant_id").isNotEmpty());

        mockMvc.perform(get("/api/v1/questions/{questionId}", ORIENTATION_QUESTION)
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question_id").value(ORIENTATION_QUESTION.toString()))
                .andExpect(jsonPath("$.order").value(1));
    }

    @Test
    void baselineSessionUsesCistQuestionsAndMarksTheUserAsCompleted() throws Exception {
        UserEntity elder = saveUser("baseline-owner", "elder");
        Instant now = Instant.now();
        consentRepository.save(new ConsentEntity(
                uuidGenerator.generate(), elder.getId(), "analysis", true, now, "test-v1", now));
        consentRepository.save(new ConsentEntity(
                uuidGenerator.generate(), elder.getId(), "voice_collection", true, now, "test-v1", now));

        String sessionBody = mockMvc.perform(post("/api/v1/sessions")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + elder.getId() + "\",\"session_type\":\"baseline\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.session_type").value("baseline"))
                .andExpect(jsonPath("$.total_questions").value(17))
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(sessionBody).get("session_id").asText());

        mockMvc.perform(get("/api/v1/questions/daily")
                        .with(jwtFor(elder))
                        .param("user_id", elder.getId().toString())
                        .param("session_type", "baseline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(17));

        mockMvc.perform(patch("/api/v1/sessions/{sessionId}/end", sessionId)
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.xp_earned").value(30));

        org.assertj.core.api.Assertions.assertThat(userRepository.findById(elder.getId()).orElseThrow().isBaselineCompleted())
                .isTrue();
    }

    @Test
    void emotionalQaSessionUsesFiveQuestions() throws Exception {
        UserEntity elder = saveUser("emotional-qa-five", "elder");
        Instant now = Instant.now();
        consentRepository.save(new ConsentEntity(
                uuidGenerator.generate(), elder.getId(), "analysis", true, now, "test-v1", now));
        consentRepository.save(new ConsentEntity(
                uuidGenerator.generate(), elder.getId(), "voice_collection", true, now, "test-v1", now));

        String sessionBody = mockMvc.perform(post("/api/v1/sessions")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + elder.getId() + "\",\"session_type\":\"emotional_qa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total_questions").value(5))
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(sessionBody).get("session_id").asText());

        mockMvc.perform(get("/api/v1/questions/daily")
                        .with(jwtFor(elder))
                        .param("user_id", elder.getId().toString())
                        .param("session_type", "emotional_qa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(5))
                .andExpect(jsonPath("$.questions[4].order").value(5));

        mockMvc.perform(patch("/api/v1/sessions/{sessionId}/end", sessionId)
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp_earned").value(20))
                .andExpect(jsonPath("$.character_level").value(1));
    }

    @Test
    void baselineSessionRequiresAnalysisAndVoiceConsents() throws Exception {
        UserEntity elder = saveUser("baseline-consent-required", "elder");

        mockMvc.perform(post("/api/v1/sessions")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + elder.getId() + "\",\"session_type\":\"baseline\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("인지 활동 분석 동의가 필요합니다."));
    }

    private UserEntity saveUser(String prefix, String role) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(
                id,
                prefix + "-" + id + "@example.com",
                null,
                "테스트 " + role,
                role,
                LocalDate.of(1945, 1, 1),
                "80s_plus",
                "female",
                null,
                false,
                now,
                now));
    }

    private UUID startCistSession(UserEntity elder) throws Exception {
        String body = mockMvc.perform(post("/api/v1/sessions")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"" + elder.getId() + "\",\"session_type\":\"cist\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).get("session_id").asText());
    }

    private RecordingEntity saveRecording(UUID userId, UUID sessionId, UUID questionId, Instant now) {
        UUID recordingId = UUID.randomUUID();
        return recordingRepository.save(new RecordingEntity(
                recordingId,
                UUID.randomUUID(),
                userId,
                RecordingEntity.ANSWER,
                sessionId,
                questionId,
                "recordings/" + recordingId + ".wav",
                "answer.wav",
                "{}",
                "audio/wav",
                128,
                1000,
                now,
                now));
    }

    private String answerJson(UUID questionId, UUID recordingId, UUID transcriptId, Instant answeredAt) {
        String transcriptField = transcriptId == null
                ? ""
                : ",\"transcript_id\":\"" + transcriptId + "\"";
        return "{\"client_answer_id\":\"" + UUID.randomUUID()
                + "\",\"question_id\":\"" + questionId
                + "\",\"recording_id\":\"" + recordingId + "\""
                + transcriptField
                + ",\"response_time_ms\":1000,\"answered_at\":\"" + answeredAt + "\"}";
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt
                .subject(user.getId().toString())
                .claim("role", user.getRole()));
    }
}
