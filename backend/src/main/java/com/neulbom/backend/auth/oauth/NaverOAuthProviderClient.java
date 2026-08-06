package com.neulbom.backend.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.neulbom.backend.auth.api.OAuthLoginRequest;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.OAuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NaverOAuthProviderClient implements OAuthProviderClient {

    private final RestClient restClient;
    private final OAuthProperties.Provider properties;

    public NaverOAuthProviderClient(RestClient.Builder restClientBuilder, OAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties.naver();
    }

    @Override
    public String provider() {
        return "naver";
    }

    @Override
    public OAuthProfile fetchProfile(OAuthLoginRequest request) {
        validateConfigured();
        try {
            JsonNode tokenResponse = restClient.get()
                    .uri(uriBuilder -> {
                        UriComponentsBuilder tokenUri = UriComponentsBuilder.fromUriString(properties.tokenUri())
                                .queryParam("grant_type", "authorization_code")
                                .queryParam("client_id", properties.clientId())
                                .queryParam("client_secret", properties.clientSecret())
                                .queryParam("code", request.authorizationCode());
                        if (request.state() != null && !request.state().isBlank()) {
                            tokenUri.queryParam("state", request.state());
                        }
                        return tokenUri.build().toUri();
                    })
                    .retrieve()
                    .body(JsonNode.class);
            String accessToken = requiredText(tokenResponse, "access_token");

            JsonNode profileResponse = restClient.get()
                    .uri(properties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode profile = profileResponse.path("response");
            String email = nullableText(profile, "email");
            return new OAuthProfile(
                    provider(),
                    requiredText(profile, "id"),
                    email,
                    nullableText(profile, "name"),
                    email != null && !email.isBlank());
        } catch (RestClientResponseException exception) {
            throw oauthFailure(exception);
        } catch (RestClientException | IllegalStateException exception) {
            throw new ExternalServiceUnavailableException("네이버 인증 서버와 통신할 수 없습니다.");
        }
    }

    private void validateConfigured() {
        if (properties == null
                || properties.clientId() == null || properties.clientId().isBlank()
                || properties.clientSecret() == null || properties.clientSecret().isBlank()) {
            throw new ExternalServiceUnavailableException("네이버 OAuth 설정이 없습니다.");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new ExternalServiceUnavailableException("네이버 응답에 필수 사용자 정보가 없습니다.");
        }
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private ApiException oauthFailure(RestClientResponseException exception) {
        if (exception.getStatusCode().is4xxClientError()) {
            return new ApiException(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다.", "네이버 authorization code를 확인하세요.");
        }
        return new ExternalServiceUnavailableException("네이버 인증 서버가 응답하지 않습니다.");
    }
}
