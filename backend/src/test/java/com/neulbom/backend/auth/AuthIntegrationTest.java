package com.neulbom.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.neulbom.backend.common.audit.AuditLogRepository;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.ServerWorkerOnly;
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
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthIntegrationTest.WorkerEndpointTestConfig.class)
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

    @Autowired
    private AuditLogRepository auditLogRepository;

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

        mockMvc.perform(post("/api/v1/auth/register")
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

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "password-1234")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.detail").value("로그인해 주세요."));
    }

    @Test
    void emailAvailabilityReportsExistingAndAvailableAddresses() throws Exception {
        String existingEmail = uniqueEmail("availability-existing");
        register(existingEmail, "password-1234");

        mockMvc.perform(get("/api/v1/auth/email/availability")
                        .param("email", existingEmail.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(existingEmail))
                .andExpect(jsonPath("$.available").value(false));

        String availableEmail = uniqueEmail("availability-free");
        mockMvc.perform(get("/api/v1/auth/email/availability")
                        .param("email", availableEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(availableEmail))
                .andExpect(jsonPath("$.available").value(true));

        mockMvc.perform(get("/api/v1/auth/email/availability")
                        .param("email", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginRefreshRotationAndLogoutRevokeTheRefreshToken() throws Exception {
        String email = uniqueEmail("token");
        register(email, "password-1234");

        AuthTokenResponse first = login(email, "password-1234");
        AuthTokenResponse rotated = objectMapper.readValue(
                mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refresh_token", first.refreshToken())))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                        .andReturn().getResponse().getContentAsString(),
                AuthTokenResponse.class);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", first.refreshToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(jwt().jwt(existing -> existing
                                .subject(rotated.userId().toString())
                                .claim("role", rotated.role())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", rotated.refreshToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
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

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reset_token\":\"" + resetToken.get()
                                + "\",\"new_password\":\"new-password-123\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reset_token\":\"" + resetToken.get()
                                + "\",\"new_password\":\"another-password-123\"}"))
                .andExpect(status().isGone());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", oldSession.refreshToken())))
                .andExpect(status().isUnauthorized());
        login(email, "new-password-123");
    }

    @Test
    void passwordResetRequestRateLimitDoesNotRevealAccountExistence() throws Exception {
        String email = uniqueEmail("rate-limit");
        String body = "{\"email\":\"" + email + "\"}";

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.error").value("요청이 너무 많습니다."));
    }

    @Test
    void passwordResetRequestLimitsIpAcrossDifferentEmails() throws Exception {
        for (int index = 0; index < 3; index++) {
            String email = uniqueEmail("ip-rate-limit-" + index);
            mockMvc.perform(post("/api/v1/auth/password/reset/request")
                            .with(request -> {
                                request.setRemoteAddr("10.0.0.2");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.2");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail("ip-rate-limit-blocked") + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void authenticatedUserChangesPasswordAndRevokesRefreshTokensByDefault() throws Exception {
        String email = uniqueEmail("change-password");
        register(email, "old-password-123");
        AuthTokenResponse session = login(email, "old-password-123");
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(jwt().jwt(existing -> existing
                                .subject(session.userId().toString())
                                .claim("role", session.role())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "current_password": "old-password-123",
                                  "new_password": "new-password-123"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", session.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "old-password-123")))
                .andExpect(status().isUnauthorized());
        login(email, "new-password-123");
        org.assertj.core.api.Assertions.assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore + 1);
    }

    @Test
    void passwordChangeRejectsWrongCurrentPasswordAndSameNewPassword() throws Exception {
        String email = uniqueEmail("invalid-change-password");
        register(email, "password-1234");
        AuthTokenResponse session = login(email, "password-1234");

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(jwt().jwt(existing -> existing
                                .subject(session.userId().toString())
                                .claim("role", session.role())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "current_password": "wrong-password",
                                  "new_password": "new-password-123"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(jwt().jwt(existing -> existing
                                .subject(session.userId().toString())
                                .claim("role", session.role())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "current_password": "password-1234",
                                  "new_password": "password-1234"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void passwordChangeKeepsRefreshTokensWhenExplicitlyRequested() throws Exception {
        String email = uniqueEmail("keep-session-password");
        register(email, "old-password-123");
        AuthTokenResponse session = login(email, "old-password-123");

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(jwt().jwt(existing -> existing
                                .subject(session.userId().toString())
                                .claim("role", session.role())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "current_password": "old-password-123",
                                  "new_password": "new-password-123",
                                  "logout_other_sessions": false
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", session.refreshToken())))
                .andExpect(status().isOk());
        login(email, "new-password-123");
    }

    @Test
    void legacyUnversionedLoginPathIsNotPublic() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("missing@example.com", "password-1234")))
                .andExpect(status().isUnauthorized());
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
        String firstResponse = mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andReturn().getResponse().getContentAsString();
        AuthTokenResponse firstSession = objectMapper.readValue(firstResponse, AuthTokenResponse.class);

        mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false));

        mockMvc.perform(delete("/api/v1/users/me")
                        .with(jwt().jwt(existing -> existing
                                .subject(firstSession.userId().toString())
                                .claim("role", firstSession.role()))))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(
                oauthAccountRepository.findByProviderAndProviderUserId("kakao", profile.providerUserId()))
                .isEmpty();
    }

    @Test
    void linkedOAuthAccountCanSignInWhenProviderOmitsEmailOnLaterProfileResponse() throws Exception {
        OAuthProviderClient client = org.mockito.Mockito.mock(OAuthProviderClient.class);
        String providerUserId = "naver-user-" + UUID.randomUUID();
        String providerEmail = uniqueEmail("oauth-email-preserved");
        OAuthProfile firstProfile = new OAuthProfile(
                "naver", providerUserId, providerEmail, "네이버 사용자", true);
        OAuthProfile laterProfileWithoutEmail = new OAuthProfile(
                "naver", providerUserId, null, null, false);
        when(oauthProviderClientRegistry.clientFor("naver")).thenReturn(client);
        when(client.fetchProfile(any())).thenReturn(firstProfile, laterProfileWithoutEmail);

        String body = "{\"authorization_code\":\"one-time-code\",\"redirect_uri\":\"http://localhost/callback\","
                + "\"role\":\"elder\"}";
        mockMvc.perform(post("/api/v1/auth/oauth/naver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true));

        mockMvc.perform(post("/api/v1/auth/oauth/naver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false));

        mockMvc.perform(post("/api/v1/auth/oauth/naver/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorization_code": "another-one-time-code",
                                  "redirect_uri": "http://localhost/callback"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("authenticated"))
                .andExpect(jsonPath("$.tokens.is_new_user").value(false));

        org.assertj.core.api.Assertions.assertThat(
                        oauthAccountRepository.findByProviderAndProviderUserId("naver", providerUserId)
                                .orElseThrow()
                                .getProviderEmail())
                .isEqualTo(providerEmail);
    }

    @Test
    void newOAuthAccountStillRequiresAnEmailWhenProviderOmitsIt() throws Exception {
        OAuthProviderClient client = org.mockito.Mockito.mock(OAuthProviderClient.class);
        OAuthProfile profileWithoutEmail = new OAuthProfile(
                "naver", "naver-new-without-email-" + UUID.randomUUID(), null, "네이버 사용자", false);
        when(oauthProviderClientRegistry.clientFor("naver")).thenReturn(client);
        when(client.fetchProfile(any())).thenReturn(profileWithoutEmail);

        mockMvc.perform(post("/api/v1/auth/oauth/naver/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorization_code": "one-time-code",
                                  "redirect_uri": "http://localhost/callback"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("검증된 이메일을 제공하는 계정만 사용할 수 있습니다."));
    }

    @Test
    void oauthPrepareAuthenticatesExistingAccountAndAsksRoleOnlyForNewAccount() throws Exception {
        OAuthProviderClient client = org.mockito.Mockito.mock(OAuthProviderClient.class);
        OAuthProfile profile = new OAuthProfile("kakao", "kakao-pending-" + UUID.randomUUID(),
                uniqueEmail("oauth-pending"), "신규 소셜 사용자", true);
        when(oauthProviderClientRegistry.clientFor("kakao")).thenReturn(client);
        when(client.fetchProfile(any())).thenReturn(profile);

        String preparedResponse = mockMvc.perform(post("/api/v1/auth/oauth/kakao/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorization_code": "one-time-code",
                                  "redirect_uri": "http://localhost/callback",
                                  "state": "state-value"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("role_required"))
                .andExpect(jsonPath("$.tokens").doesNotExist())
                .andExpect(jsonPath("$.pending_token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String pendingToken = objectMapper.readTree(preparedResponse).get("pending_token").asText();

        mockMvc.perform(post("/api/v1/auth/oauth/kakao/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pending_token\":\"" + pendingToken + "\",\"role\":\"guardian\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andExpect(jsonPath("$.role").value("guardian"));

        mockMvc.perform(post("/api/v1/auth/oauth/kakao/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorization_code": "another-one-time-code",
                                  "redirect_uri": "http://localhost/callback"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("authenticated"))
                .andExpect(jsonPath("$.tokens.is_new_user").value(false))
                .andExpect(jsonPath("$.tokens.role").value("guardian"))
                .andExpect(jsonPath("$.pending_token").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/oauth/kakao/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pending_token\":\"" + pendingToken + "\",\"role\":\"elder\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void oauthProviderFailureReturnsSafeServiceUnavailableResponse() throws Exception {
        OAuthProviderClient client = org.mockito.Mockito.mock(OAuthProviderClient.class);
        when(oauthProviderClientRegistry.clientFor("kakao")).thenReturn(client);
        when(client.fetchProfile(any())).thenThrow(new ExternalServiceUnavailableException("소셜 provider 응답 지연"));

        mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorization_code": "secret-one-time-code",
                                  "redirect_uri": "http://localhost/callback",
                                  "role": "elder"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.detail").value("소셜 provider 응답 지연"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getContentAsString())
                        .doesNotContain("secret-one-time-code"));
    }

    @Test
    void accountWithdrawalAnonymizesUserAndRevokesRefreshToken() throws Exception {
        String email = uniqueEmail("withdraw");
        register(email, "password-1234");
        AuthTokenResponse session = login(email, "password-1234");

        mockMvc.perform(delete("/api/v1/users/me")
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

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refresh_token", session.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "password-1234")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectMissingAndMalformedAccessTokens() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serverWorkerEndpointRejectsAppRoleAndAcceptsServerScope() throws Exception {
        mockMvc.perform(get("/api/v1/internal/worker-test")
                        .with(jwt().jwt(existing -> existing
                                .subject(UUID.randomUUID().toString())
                                .claim("role", "elder"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/internal/worker-test")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_server:write"))))
                .andExpect(status().isNoContent());
    }

    @TestConfiguration
    static class WorkerEndpointTestConfig {

        @RestController
        static class WorkerEndpoint {

            @GetMapping("/api/v1/internal/worker-test")
            @ServerWorkerOnly
            ResponseEntity<Void> execute() {
                return ResponseEntity.noContent().build();
            }
        }
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isCreated());
    }

    private AuthTokenResponse login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
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
