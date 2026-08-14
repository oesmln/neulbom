package com.neulbom.backend.speech;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.speech.integration.TextToSpeechClient;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.external-api.google-tts-project-id=neulbom-test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpeechIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private TextToSpeechClient textToSpeechClient;

    @Test
    void elderCanSynthesizeGoogleTtsAudio() throws Exception {
        UserEntity elder = saveUser("elder");
        when(textToSpeechClient.isConfigured()).thenReturn(true);
        when(textToSpeechClient.synthesize(
                eq("안녕하세요"), eq("ko-KR"), eq("ko-KR-Neural2-A"), any(BigDecimal.class)))
                .thenReturn(new TextToSpeechClient.SynthesisResult(
                        new byte[]{1, 2, 3}, "audio/mpeg", "ko-KR-Neural2-A"));

        mockMvc.perform(post("/api/v1/speech/synthesize")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"안녕하세요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audio_content_base64").value("AQID"))
                .andExpect(jsonPath("$.content_type").value("audio/mpeg"))
                .andExpect(jsonPath("$.voice_profile_id").value("voice_ko_01"))
                .andExpect(jsonPath("$.voice_name").value("ko-KR-Neural2-A"))
                .andExpect(jsonPath("$.speech_rate").value(0.90));
    }

    @Test
    void synthesizeRejectsBlankTextAndGuardianRole() throws Exception {
        UserEntity elder = saveUser("elder");
        UserEntity guardian = saveUser("guardian");

        mockMvc.perform(post("/api/v1/speech/synthesize")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/speech/synthesize")
                        .with(jwtFor(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"안녕하세요\"}"))
                .andExpect(status().isForbidden());
    }

    private UserEntity saveUser(String role) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(
                id,
                role + "-" + id + "@example.com",
                null,
                "테스트 사용자",
                role,
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
