package com.neulbom.backend.analysis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.session.AnswerEntity;
import com.neulbom.backend.session.AnswerRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CistFusionFeatureIntegrationTest {

    private static final Map<String, UUID> QUESTION_IDS = Map.of(
            "orientation", UUID.fromString("00000000-0000-0000-0000-000000000101"),
            "memory", UUID.fromString("00000000-0000-0000-0000-000000000103"),
            "attention", UUID.fromString("00000000-0000-0000-0000-000000000104"),
            "language", UUID.fromString("00000000-0000-0000-0000-000000000105"));

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private AnswerRepository answerRepository;
    @Autowired private CistItemEvaluationRepository evaluationRepository;
    @Autowired private UuidGenerator uuidGenerator;

    @Test
    void aggregatesFourCategoriesKeepsZeroDelayAndIsIdempotent() throws Exception {
        UserEntity elder = saveUser("fusion-feature");
        Instant now = Instant.now();
        SessionEntity session = sessionRepository.save(new SessionEntity(
                uuidGenerator.generate(), elder.getId(), "cist", 5, "{}", false, now));
        int[] delays = {0, 580, 940, 1800};
        int index = 0;
        for (String category : CistMetadataFeatureCalculator.CORE_CATEGORIES) {
            UUID questionId = QUESTION_IDS.get(category);
            UUID answerId = uuidGenerator.generate();
            answerRepository.save(new AnswerEntity(
                    answerId,
                    session.getId(),
                    questionId,
                    uuidGenerator.generate(),
                    "답변",
                    null,
                    null,
                    delays[index],
                    now,
                    now));
            evaluationRepository.save(new CistItemEvaluationEntity(
                    uuidGenerator.generate(),
                    answerId,
                    session.getId(),
                    questionId,
                    category,
                    "completed",
                    true,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    delays[index],
                    "orientation".equals(category),
                    "orientation".equals(category) ? "manual_note" : null,
                    "cist-rubric-v1",
                    "{}",
                    now,
                    now));
            index++;
        }

        String request = """
                {
                  "user_id": "%s",
                  "session_id": "%s",
                  "ast_score": 0.8,
                  "kc_electra_score": 0.7,
                  "feature_version": "cist-feature-v1",
                  "scaler_version": "fusion-scaler-v1"
                }
                """.formatted(elder.getId(), session.getId());

        String first = mockMvc.perform(post("/api/v1/analysis/cist/features")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category_balanced_wrong_event_score").value(0.125))
                .andExpect(jsonPath("$.category_balanced_median_delay").value(0.83))
                .andReturn().getResponse().getContentAsString();

        String featureId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(first).get("feature_id").asText();
        mockMvc.perform(post("/api/v1/analysis/cist/features")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feature_id").value(featureId));
    }

    private UserEntity saveUser(String prefix) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(
                id, prefix + "-" + id + "@example.com", null, "융합 사용자", "elder",
                LocalDate.of(1945, 1, 1), "80s_plus", "female", null, false, now, now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor serverJwt(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()))
                .authorities(new SimpleGrantedAuthority("SCOPE_server:write"));
    }
}
