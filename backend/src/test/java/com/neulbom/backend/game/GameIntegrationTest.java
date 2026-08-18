package com.neulbom.backend.game;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.common.id.UuidGenerator;
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
class GameIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private XpLedgerRepository xpLedgerRepository;
    @Autowired private UuidGenerator uuidGenerator;

    @Test
    void gameResultAndXpAreIdempotentAndCharacterHistoryIsExposed() throws Exception {
        UserEntity elder = saveUser("game-owner");
        SessionEntity session = sessionRepository.save(new SessionEntity(uuidGenerator.generate(), elder.getId(), "game", 6, "{}", false, Instant.now()));
        UUID clientResultId = UUID.randomUUID();
        String request = """
                {
                  "user_id":"%s", "session_id":"%s", "client_game_result_id":"%s",
                  "game_type":"image_match", "score":6, "response_times":[1.2,0.8],
                  "error_count":0, "total_questions":6, "matched_pairs":6, "attempt_count":7,
                  "duration_sec":42, "restarted_count":0, "completed":true
                }
                """.formatted(elder.getId(), session.getId(), clientResultId);

        mockMvc.perform(post("/api/v1/game/result").with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cognitive_index").value(100.0))
                .andExpect(jsonPath("$.xp_earned").value(13)).andExpect(jsonPath("$.deduplicated").value(false));
        mockMvc.perform(post("/api/v1/game/result").with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.xp_earned").value(13)).andExpect(jsonPath("$.deduplicated").value(true));
        mockMvc.perform(get("/api/v1/game/{userId}/history", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.records[0].xp_earned").value(13));
        String colorMatchRequest = """
                {
                  "user_id":"%s", "session_id":"%s", "client_game_result_id":"%s",
                  "game_type":"color_match", "score":4, "response_times":[1.1,1.3,0.9,1.0,1.2],
                  "error_count":1, "total_questions":5, "duration_sec":30,
                  "restarted_count":0, "completed":true
                }
                """.formatted(elder.getId(), session.getId(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/game/result").with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON).content(colorMatchRequest))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cognitive_index").value(80.0));
        mockMvc.perform(get("/api/v1/character/{userId}", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.xp_current").value(26))
                .andExpect(jsonPath("$.xp_goal").value(100))
                .andExpect(jsonPath("$.stage").value("egg"));
        mockMvc.perform(get("/api/v1/character/{userId}/xp-history", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records[0].reason").value("game"));
        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"attendance\",\"event_id\":\"attendance-1\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"attendance\",\"event_id\":\"attendance-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.xp_current").value(36))
                .andExpect(jsonPath("$.awarded_amount").value(10));
        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"attendance\",\"event_id\":\"attendance-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deduplicated").value(true));
    }

    @Test
    void dailyXpCapClipsAwardsAndRecordsZeroAmountEventsAsDeduplicated() throws Exception {
        UserEntity elder = saveUser("daily-cap-owner");

        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":60,\"reason\":\"visit\",\"event_id\":\"daily-cap-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awarded_amount").value(60))
                .andExpect(jsonPath("$.xp_current").value(60));

        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":60,\"reason\":\"visit\",\"event_id\":\"daily-cap-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awarded_amount").value(40))
                .andExpect(jsonPath("$.xp_current").value(100))
                .andExpect(jsonPath("$.level").value(2));

        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"visit\",\"event_id\":\"daily-cap-3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awarded_amount").value(0))
                .andExpect(jsonPath("$.deduplicated").value(false));

        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"visit\",\"event_id\":\"daily-cap-3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awarded_amount").value(0))
                .andExpect(jsonPath("$.deduplicated").value(true));

        mockMvc.perform(get("/api/v1/character/{userId}/xp-history", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records.length()").value(2));
    }

    @Test
    void threeDayActivityStreakAwardsTenBonusXp() throws Exception {
        UserEntity elder = saveUser("streak-owner");
        Instant now = Instant.now();
        xpLedgerRepository.save(new XpLedgerEntity(
                uuidGenerator.generate(), elder.getId(), "streak-activity-1", 3, "game", now.minusSeconds(2 * 86_400L)));
        xpLedgerRepository.save(new XpLedgerEntity(
                uuidGenerator.generate(), elder.getId(), "streak-activity-2", 3, "game", now.minusSeconds(86_400L)));

        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20,\"reason\":\"emotional_qa\",\"event_id\":\"streak-activity-3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awarded_amount").value(20))
                .andExpect(jsonPath("$.xp_current").value(30));

        mockMvc.perform(get("/api/v1/character/{userId}/xp-history", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].reason").value("streak"))
                .andExpect(jsonPath("$.records[0].amount").value(10));
    }

    private UserEntity saveUser(String prefix) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(id, prefix + "-" + id + "@example.com", null, prefix, "elder",
                LocalDate.of(1945, 1, 1), "80s_plus", "female", null, false, now, now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor serverJwt(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()))
                .authorities(new SimpleGrantedAuthority("SCOPE_server:write"));
    }
}
