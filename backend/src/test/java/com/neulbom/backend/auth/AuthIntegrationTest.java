package com.neulbom.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.auth.api.AuthTokenResponse;
import com.neulbom.backend.auth.oauth.OAuthProfile;
import com.neulbom.backend.auth.oauth.OAuthProviderClient;
import com.neulbom.backend.auth.oauth.OAuthProviderClientRegistry;
import com.neulbom.backend.auth.service.PasswordResetNotifier;
import com.neulbom.backend.user.OAuthAccountRepository;
import com.neulbom.backend.user.RefreshTokenRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OAuthAccountRepository oauthAccountRepository;

    @MockitoBean
    private PasswordResetNotifier passwordResetNotifier;

    @MockitoBean
    private OAuthProviderClientRegistry oauthProviderClientRegistry;

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(passwordResetNotifier, oauthProviderClientRegistry);
    }

    @Test
    void registerStoresBcryptHashAndRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail("register");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "password-1234")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").isNotEmpty())
                .andExpect(jsonPath("$.role").value("elder"))
                .andExpect(jsonPath("$.password").doesNotExist());

        UserEntity user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getPasswordHash())
                .startsWith("$2a$")
                .doesNotContain("password-1234");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "password-1234")))
                .andExpect(status().isConflict());
    }

    @Test
    void loginRefreshRotationAndLogoutRevokeTheRefreshToken() throws Exception {
        String email = uniqueEmail("token");
        register(email, "password-1234");

        AuthTokenResponse first = login(email, "password-1234");
        AuthTokenResponse rotated = objectMapper.readValue(
                mockMvc.perform(post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refresh_token", first.refreshToken())))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                        .andReturn().getResponse().getContentAsString(),
                AuthTokenResponse.class);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", first.refreshToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/logout")
                        .with(jwt().jwt(existing -> existing
                                .subject(rotated.userId().toString())
                                .claim("role", rotated.role())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", rotated.refreshToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", rotated.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetIsOneTimeAndRevokesExistingSessions() throws Exception {
        String email = uniqueEmail("reset");
        register(email, "old-password-123");
        AuthTokenResponse oldSession = login(email, "old-password-123");
        AtomicReference<String> resetToken = new AtomicReference<>();
        doAnswer(invocation -> {
            resetToken.set(invocation.getArgument(1, String.class));
            return null;
        }).when(passwordResetNotifier).send(anyString(), anyString(), any(Instant.class));

        mockMvc.perform(post("/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        mockMvc.perform(post("/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reset_token\":\"" + resetToken.get()
                                + "\",\"new_password\":\"new-password-123\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reset_token\":\"" + resetToken.get()
                                + "\",\"new_password\":\"another-password-123\"}"))
                .andExpect(status().isGone());
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", oldSession.refreshToken())))
                .andExpect(status().isUnauthorized());
        login(email, "new-password-123");
    }

    @Test
    void oauthLoginCreatesAndThenReusesTheLinkedAccount() throws Exception {
        OAuthProviderClient client = org.mockito.Mockito.mock(OAuthProviderClient.class);
        OAuthProfile profile = new OAuthProfile("kakao", "kakao-user-" + UUID.randomUUID(),
                uniqueEmail("oauth"), "카카오 사용자", true);
        when(oauthProviderClientRegistry.clientFor("kakao")).thenReturn(client);
        when(client.fetchProfile(any())).thenReturn(profile);

        String body = "{\"authorization_code\":\"one-time-code\",\"redirect_uri\":\"http://localhost/callback\","
                + "\"role\":\"elder\"}";
        String firstResponse = mockMvc.perform(post("/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andReturn().getResponse().getContentAsString();
        AuthTokenResponse firstSession = objectMapper.readValue(firstResponse, AuthTokenResponse.class);

        mockMvc.perform(post("/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false));

        mockMvc.perform(delete("/users/me")
                        .with(jwt().jwt(existing -> existing
                                .subject(firstSession.userId().toString())
                                .claim("role", firstSession.role()))))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(
                oauthAccountRepository.findByProviderAndProviderUserId("kakao", profile.providerUserId()))
                .isEmpty();
    }

    @Test
    void accountWithdrawalAnonymizesUserAndRevokesRefreshToken() throws Exception {
        String email = uniqueEmail("withdraw");
        register(email, "password-1234");
        AuthTokenResponse session = login(email, "password-1234");

        mockMvc.perform(delete("/users/me")
                        .with(jwt().jwt(existing -> existing
                                .subject(session.userId().toString())
                                .claim("role", session.role())))
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent());

        UserEntity withdrawn = userRepository.findById(session.userId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(withdrawn.getAccountStatus()).isEqualTo(UserEntity.WITHDRAWN);
        org.assertj.core.api.Assertions.assertThat(withdrawn.getEmail()).startsWith("withdrawn-");
        org.assertj.core.api.Assertions.assertThat(withdrawn.getPasswordHash()).isNull();
        org.assertj.core.api.Assertions.assertThat(refreshTokenRepository.findByTokenHash("not-a-real-hash")).isEmpty();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", session.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "password-1234")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectMissingAndMalformedAccessTokens() throws Exception {
        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/users/me").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isCreated());
    }

    private AuthTokenResponse login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, AuthTokenResponse.class);
    }

    private String registerJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"name\":\"테스트 사용자\",\"role\":\"elder\"}";
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String json(String field, String value) {
        return "{\"" + field + "\":\"" + value + "\"}";
    }

    private String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@example.com";
    }
}
