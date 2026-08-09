package com.neulbom.backend.notification;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.id.UuidGenerator;
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
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UuidGenerator uuidGenerator;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void workerPushListFilterReadAndReadAllAreOwnerSafeAndIdempotent() throws Exception {
        UserEntity elder = saveUser("notification-owner");
        UserEntity other = saveUser("notification-other");
        String eventId = "analysis-event-" + UUID.randomUUID();
        String request = """
                {
                  "target_user_id":"%s",
                  "title":"인지 활동 결과 업데이트",
                  "body":"최근 인지 활동 결과를 확인해 보세요.",
                  "type":"screening_updated",
                  "severity":"success",
                  "status_label":"완료",
                  "data":{
                    "event_id":"%s",
                    "target_route":"/screenings/result",
                    "reference_type":"screening",
                    "reference_id":"%s",
                    "elder_id":"%s"
                  }
                }
                """.formatted(elder.getId(), eventId, UUID.randomUUID(), elder.getId());

        String firstBody = mockMvc.perform(post("/api/v1/notifications/push")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notification_id").isNotEmpty())
                .andExpect(jsonPath("$.sent_at").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(firstBody).get("notification_id").asText();

        mockMvc.perform(post("/api/v1/notifications/push")
                        .with(serverJwt(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notification_id").value(firstId));

        mockMvc.perform(get("/api/v1/notifications/{userId}", elder.getId())
                        .with(jwtFor(elder))
                        .param("unread_only", "true")
                        .param("type", "screening_updated")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].is_read").value(false))
                .andExpect(jsonPath("$.notifications[0].data.event_id").value(eventId))
                .andExpect(jsonPath("$.unread_count").value(1));

        mockMvc.perform(get("/api/v1/notifications/{userId}", elder.getId()).with(jwtFor(other)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", firstId).with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notification_id").value(firstId))
                .andExpect(jsonPath("$.is_read").value(true))
                .andExpect(jsonPath("$.read_at").isNotEmpty());

        mockMvc.perform(get("/api/v1/notifications/{userId}", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_count").value(0));

        mockMvc.perform(patch("/api/v1/notifications/read-all").with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated_count").value(0))
                .andExpect(jsonPath("$.read_at").isNotEmpty());
    }

    @Test
    void appTokenCannotCreateNotification() throws Exception {
        UserEntity elder = saveUser("notification-client");

        mockMvc.perform(post("/api/v1/notifications/push")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target_user_id":"%s",
                                  "title":"알림",
                                  "body":"내용",
                                  "type":"reminder"
                                }
                                """.formatted(elder.getId())))
                .andExpect(status().isForbidden());
    }

    private UserEntity saveUser(String prefix) {
        UUID id = uuidGenerator.generate();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(
                id,
                prefix + "-" + id + "@example.com",
                null,
                prefix,
                "elder",
                LocalDate.of(1945, 1, 1),
                "80s_plus",
                "female",
                null,
                false,
                now,
                now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor serverJwt(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()))
                .authorities(new SimpleGrantedAuthority("SCOPE_server:write"));
    }
}
