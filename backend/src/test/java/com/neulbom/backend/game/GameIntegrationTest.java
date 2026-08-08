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
    @Autowired private UuidGenerator uuidGenerator;

    @Test
    void gameResultAndXpAreIdempotentAndCharacterHistoryIsExposed() throws Exception {
        UserEntity elder = saveUser("game-owner");
        SessionEntity session = sessionRepository.save(new SessionEntity(uuidGenerator.generate(), elder.getId(), "game", 6, "{}", false, Instant.now()));
        UUID clientResultId = UUID.randomUUID();
        String request = """
                {
                  "user_id":"%s", "session_id":"%s", "client_game_result_id":"%s",
                  "game_type":"image_match", "score":5, "response_times":[1.2,0.8],
                  "error_count":1, "total_questions":6, "matched_pairs":5, "attempt_count":7,
                  "duration_sec":42, "restarted_count":0, "completed":true
                }
                """.formatted(elder.getId(), session.getId(), clientResultId);

        mockMvc.perform(post("/api/v1/game/result").with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cognitive_index").value(83.33))
                .andExpect(jsonPath("$.xp_earned").value(30)).andExpect(jsonPath("$.deduplicated").value(false));
        mockMvc.perform(post("/api/v1/game/result").with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.xp_earned").value(30)).andExpect(jsonPath("$.deduplicated").value(true));
        mockMvc.perform(get("/api/v1/game/{userId}/history", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.records[0].xp_earned").value(30));
        mockMvc.perform(get("/api/v1/character/{userId}", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.xp_current").value(30))
                .andExpect(jsonPath("$.stage").value("egg"));
        mockMvc.perform(get("/api/v1/character/{userId}/xp-history", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records[0].reason").value("game"));
        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"attendance\",\"event_id\":\"attendance-1\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"attendance\",\"event_id\":\"attendance-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.xp_current").value(40));
        mockMvc.perform(post("/api/v1/character/{userId}/xp", elder.getId()).with(serverJwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10,\"reason\":\"attendance\",\"event_id\":\"attendance-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deduplicated").value(true));
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
