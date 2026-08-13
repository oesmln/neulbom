package com.neulbom.backend.counseling;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.guardian.GuardianLinkEntity;
import com.neulbom.backend.guardian.GuardianLinkRepository;
import com.neulbom.backend.guardian.GuardianLinkScopeEntity;
import com.neulbom.backend.guardian.GuardianLinkScopeRepository;
import com.neulbom.backend.user.ConsentEntity;
import com.neulbom.backend.user.ConsentRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CounselingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private GuardianLinkRepository linkRepository;
    @Autowired private GuardianLinkScopeRepository linkScopeRepository;
    @Autowired private ConsentRepository consentRepository;

    @Test
    void regionSelectionReturnsCounselingCentersAndExternalLinks() throws Exception {
        UserEntity user = saveUser();
        mockMvc.perform(get("/api/v1/counseling/centers").with(jwtFor(user)).param("province_code", "26").param("district_code", "26350"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.centers[0].naver_map_url").isNotEmpty())
                .andExpect(jsonPath("$.centers[0].homepage_url").isNotEmpty())
                .andExpect(jsonPath("$.centers[0].reservation_mode").value("external_link"));
        mockMvc.perform(get("/api/v1/counseling/centers").with(jwtFor(user)).param("province_code", "26").param("facility_type", "dementia_center"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.centers[0].name").value("해운대구 치매안심센터"));
        mockMvc.perform(get("/api/v1/counseling/centers").with(jwtFor(user)).param("province_code", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/v1/counseling/centers").with(jwtFor(user)).param("province_code", "26").param("facility_type", "unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void guardianCanCreateListAndCancelOwnedAppointmentOnly() throws Exception {
        UserEntity guardian = saveUser("guardian");
        UserEntity elder = saveUser("elder");
        Instant now = Instant.now();
        consentRepository.save(new ConsentEntity(UUID.randomUUID(), elder.getId(), "guardian_access", true, now, "v1", now));
        GuardianLinkEntity link = linkRepository.save(new GuardianLinkEntity(
                UUID.randomUUID(), guardian.getId(), elder.getId(), "자녀", GuardianLinkEntity.ACTIVE, true, now, now));
        linkScopeRepository.save(new GuardianLinkScopeEntity(link.getId(), "summary"));

        String body = """
                {
                  "elder_id": "%s",
                  "center_id": "00000000-0000-0000-0000-000000009001",
                  "appointment_at": "2099-08-20T01:00:00Z",
                  "consultation_type": "neurology",
                  "privacy_agreed": true
                }
                """.formatted(elder.getId());

        String response = mockMvc.perform(post("/api/v1/counseling/appointments")
                        .with(jwtFor(guardian))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("requested"))
                .andReturn().getResponse().getContentAsString();
        String appointmentId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("appointment_id").asText();

        mockMvc.perform(get("/api/v1/counseling/appointments").with(jwtFor(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(delete("/api/v1/counseling/appointments/" + appointmentId).with(jwtFor(guardian)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/counseling/appointments").with(jwtFor(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments[0].status").value("cancelled"));
    }

    private UserEntity saveUser() {
        return saveUser("elder");
    }

    private UserEntity saveUser(String role) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(id, "counseling-" + role + "-" + id + "@example.com", null, "상담 사용자", role,
                LocalDate.of(1945, 1, 1), "80s_plus", "female", null, false, now, now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()));
    }
}
