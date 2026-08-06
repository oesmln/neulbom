package com.neulbom.backend.auth.oauth;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.neulbom.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OAuthProviderClientRegistry {

    private final Map<String, OAuthProviderClient> clients;

    public OAuthProviderClientRegistry(java.util.List<OAuthProviderClient> clients) {
        this.clients = clients.stream().collect(Collectors.toUnmodifiableMap(
                client -> client.provider().toLowerCase(), Function.identity()));
    }

    public OAuthProviderClient clientFor(String provider) {
        OAuthProviderClient client = clients.get(provider.toLowerCase());
        if (client == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다.", "provider는 kakao 또는 naver여야 합니다.");
        }
        return client;
    }
}
