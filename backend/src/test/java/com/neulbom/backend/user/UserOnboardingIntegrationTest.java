package com.neulbom.backend.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
class UserOnboardingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void profileCanBeReadAndPartiallyUpdatedByTheOwner() throws Exception {
        UserEntity user = saveUser("onboarding-profile");

        mockMvc.perform(get("/api/v1/users/{userId}", user.getId())
                        .with(jwtFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(user.getId().toString()))
                .andExpect(jsonPath("$.profile_completed").value(false))
                .andExpect(jsonPath("$.health_conditions").isArray());

        mockMvc.perform(patch("/api/v1/users/{userId}", user.getId())
                        .with(jwtFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "김영자",
                                  "birth_date": "1945-05-01",
                                  "age_group": "80s_plus",
                                  "gender": "female",
                                  "education_years": 12,
                                  "literacy": true,
                                  "health_conditions": ["고혈압"],
                                  "alcohol_use": "none",
                                  "smoking_status": "former",
                                  "hearing_status": "difficulty",
                                  "communication_difficulty": false,
                                  "smartphone_skill": "medium"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(user.getId().toString()))
                .andExpect(jsonPath("$.profile_completed").value(true));

        mockMvc.perform(get("/api/v1/users/{userId}", user.getId())
                        .with(jwtFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김영자"))
                .andExpect(jsonPath("$.health_conditions[0]").value("고혈압"))
                .andExpect(jsonPath("$.hearing_status").value("difficulty"));
    }

    @Test
    void profileEndpointsRejectAccessToAnotherUser() throws Exception {
        UserEntity owner = saveUser("onboarding-owner");
        UserEntity other = saveUser("onboarding-other");

        mockMvc.perform(get("/api/v1/users/{userId}", other.getId())
                        .with(jwtFor(owner)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/users/{userId}", other.getId())
                        .with(jwtFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"변경 시도\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onboardingProgressAndCharacterNameArePersistedWithoutRequiredProfileFields() throws Exception {
        UserEntity user = saveUser("onboarding-progress");

        mockMvc.perform(patch("/api/v1/users/{userId}", user.getId())
                        .with(jwtFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "onboarding_step": "character_name",
                                  "character_name": "봄이"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarding_step").value("character_name"))
                .andExpect(jsonPath("$.onboarding_completed").value(false))
                .andExpect(jsonPath("$.baseline_completed").value(false))
                .andExpect(jsonPath("$.character_name").value("봄이"));

        mockMvc.perform(get("/api/v1/users/{userId}", user.getId())
                        .with(jwtFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarding_step").value("character_name"))
                .andExpect(jsonPath("$.character_name").value("봄이"));

        mockMvc.perform(get("/api/v1/character/{userId}", user.getId())
                        .with(jwtFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("봄이"));
    }

    @Test
    void preferencesUseDocumentedDefaultsAndCanBePatched() throws Exception {
        UserEntity user = saveUser("onboarding-preferences");

        mockMvc.perform(get("/api/v1/users/{userId}/preferences", user.getId())
                        .with(jwtFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferred_hearing_side").value("unknown"))
                .andExpect(jsonPath("$.speech_rate").value(0.90))
                .andExpect(jsonPath("$.push_notification_enabled").value(true))
                .andExpect(jsonPath("$.diary_notification_enabled").value(true));

        mockMvc.perform(patch("/api/v1/users/{userId}/preferences", user.getId())
                        .with(jwtFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferred_hearing_side": "right",
                                  "voice_profile_id": "voice_ko_02",
                                  "speech_rate": 1.1,
                                  "subtitle_enabled": true,
                                  "push_notification_enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferred_hearing_side").value("right"))
                .andExpect(jsonPath("$.voice_profile_id").value("voice_ko_02"))
                .andExpect(jsonPath("$.speech_rate").value(1.1))
                .andExpect(jsonPath("$.subtitle_enabled").value(true))
                .andExpect(jsonPath("$.push_notification_enabled").value(false));

        mockMvc.perform(patch("/api/v1/users/{userId}/preferences", user.getId())
                        .with(jwtFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speech_rate\": 1.3}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/users/{userId}/preferences", user.getId())
                        .with(jwtFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voice_profile_id\": \"not-active\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void voiceProfilesAreFilteredToActiveLanguageProfiles() throws Exception {
        UserEntity user = saveUser("onboarding-voices");

        mockMvc.perform(get("/api/v1/voice-profiles")
                        .with(jwtFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voice_profiles").isArray())
                .andExpect(jsonPath("$.voice_profiles.length()").value(2))
                .andExpect(jsonPath("$.voice_profiles[0].voice_profile_id").value("voice_ko_01"));
    }

    @Test
    void consentIsVersionedAndReturnedForTheOwner() throws Exception {
        UserEntity user = saveUser("onboarding-consent");

        mockMvc.perform(post("/api/v1/consent/{userId}", user.getId())
                        .with(jwtFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "consent_type": "analysis",
                                  "agreed": true,
                                  "agreed_at": "2026-08-08T10:00:00Z",
                                  "version": "2026-08"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consent_type").value("analysis"))
                .andExpect(jsonPath("$.agreed").value(true))
                .andExpect(jsonPath("$.version").value("2026-08"));

        mockMvc.perform(get("/api/v1/consent/{userId}", user.getId())
                        .with(jwtFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents.length()").value(1))
                .andExpect(jsonPath("$.consents[0].consent_type").value("analysis"));

        mockMvc.perform(post("/api/v1/consent/{userId}", user.getId())
                        .with(jwtFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "consent_type": "not-supported",
                                  "agreed": true,
                                  "agreed_at": "2026-08-08T10:00:00Z",
                                  "version": "2026-08"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private UserEntity saveUser(String prefix) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(
                id,
                prefix + "-" + id + "@example.com",
                null,
                "테스트 사용자",
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
        return jwt().jwt(jwt -> jwt
                .subject(user.getId().toString())
                .claim("role", user.getRole()));
    }
}
