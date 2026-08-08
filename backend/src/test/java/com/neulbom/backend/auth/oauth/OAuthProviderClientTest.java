package com.neulbom.backend.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.neulbom.backend.auth.api.OAuthLoginRequest;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.config.OAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OAuthProviderClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private OAuthProperties properties;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        properties = new OAuthProperties(
                new OAuthProperties.Provider(
                        "kakao-client",
                        "kakao-secret",
                        "https://kakao.test/token",
                        "https://kakao.test/user",
                        "http://localhost/callback"),
                new OAuthProperties.Provider(
                        "naver-client",
                        "naver-secret",
                        "https://naver.test/token",
                        "https://naver.test/user",
                        "http://localhost/callback"));
    }

    @Test
    void kakaoExchangesCodeAndMapsVerifiedProfile() {
        server.expect(requestTo("https://kakao.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kakao.test/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess(
                        "{\"id\":123,\"properties\":{\"nickname\":\"카카오 사용자\"},"
                                + "\"kakao_account\":{\"email\":\"kakao@example.com\","
                                + "\"is_email_valid\":true,\"is_email_verified\":true}}",
                        MediaType.APPLICATION_JSON));

        OAuthProfile profile = new KakaoOAuthProviderClient(restClientBuilder, properties)
                .fetchProfile(new OAuthLoginRequest("kakao-code", "http://localhost/callback", "elder", "state"));

        assertThat(profile.provider()).isEqualTo("kakao");
        assertThat(profile.providerUserId()).isEqualTo("123");
        assertThat(profile.email()).isEqualTo("kakao@example.com");
        assertThat(profile.displayName()).isEqualTo("카카오 사용자");
        assertThat(profile.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void naverExchangesCodeAndMapsProfile() {
        server.expect(requestTo("https://naver.test/token?grant_type=authorization_code&client_id=naver-client"
                        + "&client_secret=naver-secret&code=naver-code&state=state"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"access_token\":\"naver-access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://naver.test/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer naver-access"))
                .andRespond(withSuccess(
                        "{\"response\":{\"id\":\"naver-user\",\"email\":\"naver@example.com\","
                                + "\"name\":\"네이버 사용자\"}}",
                        MediaType.APPLICATION_JSON));

        OAuthProfile profile = new NaverOAuthProviderClient(restClientBuilder, properties)
                .fetchProfile(new OAuthLoginRequest("naver-code", "http://localhost/callback", "elder", "state"));

        assertThat(profile.provider()).isEqualTo("naver");
        assertThat(profile.providerUserId()).isEqualTo("naver-user");
        assertThat(profile.email()).isEqualTo("naver@example.com");
        assertThat(profile.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void rejectsRedirectUriOutsideProviderAllowlistBeforeCallingProvider() {
        OAuthLoginRequest request = new OAuthLoginRequest(
                "one-time-code", "https://attacker.example/callback", "elder", null);

        assertThatThrownBy(() -> new KakaoOAuthProviderClient(restClientBuilder, properties).fetchProfile(request))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(400));
    }
}
