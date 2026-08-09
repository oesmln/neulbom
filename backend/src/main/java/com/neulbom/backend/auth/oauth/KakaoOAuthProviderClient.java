package com.neulbom.backend.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.neulbom.backend.auth.api.OAuthLoginRequest;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.OAuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoOAuthProviderClient implements OAuthProviderClient {

    private final RestClient restClient;
    private final OAuthProperties.Provider properties;

    public KakaoOAuthProviderClient(RestClient.Builder restClientBuilder, OAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties.kakao();
    }

    @Override
    public String provider() {
        return "kakao";
    }

    @Override
    public OAuthProfile fetchProfile(OAuthLoginRequest request) {
        validateConfigured(request.redirectUri());
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", properties.clientId());
            form.add("redirect_uri", request.redirectUri());
            form.add("code", request.authorizationCode());
            if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
                form.add("client_secret", properties.clientSecret());
            }

            JsonNode tokenResponse = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String accessToken = requiredText(tokenResponse, "access_token");

            JsonNode profile = restClient.get()
                    .uri(properties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode account = profile.path("kakao_account");
            String email = nullableText(account, "email");
            boolean emailVerified = account.path("is_email_valid").asBoolean(false)
                    && account.path("is_email_verified").asBoolean(false);
            return new OAuthProfile(
                    provider(),
                    requiredText(profile, "id"),
                    email,
                    nullableText(profile.path("properties"), "nickname"),
                    emailVerified);
        } catch (RestClientResponseException exception) {
            throw oauthFailure(exception);
        } catch (RestClientException | IllegalStateException exception) {
            throw new ExternalServiceUnavailableException("카카오 인증 서버와 통신할 수 없습니다.");
        }
    }

    private void validateConfigured(String redirectUri) {
        if (properties == null || properties.clientId() == null || properties.clientId().isBlank()) {
            throw new ExternalServiceUnavailableException("카카오 OAuth 설정이 없습니다.");
        }
        if (!properties.allowsRedirectUri(redirectUri)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "소셜 로그인 요청이 올바르지 않습니다.",
                    "등록되지 않은 카카오 redirect_uri입니다.");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new ExternalServiceUnavailableException("카카오 응답에 필수 사용자 정보가 없습니다.");
        }
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private ApiException oauthFailure(RestClientResponseException exception) {
        if (exception.getStatusCode().is4xxClientError()) {
            return new ApiException(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다.", "카카오 authorization code를 확인하세요.");
        }
        return new ExternalServiceUnavailableException("카카오 인증 서버가 응답하지 않습니다.");
    }
}
