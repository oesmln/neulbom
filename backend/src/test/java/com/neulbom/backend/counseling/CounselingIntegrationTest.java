package com.neulbom.backend.counseling;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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

    private UserEntity saveUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(id, "counseling-" + id + "@example.com", null, "상담 사용자", "elder",
                LocalDate.of(1945, 1, 1), "80s_plus", "female", null, false, now, now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()));
    }
}
