package com.neulbom.backend.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(
        Provider kakao,
        Provider naver
) {

    public record Provider(
            String clientId,
            String clientSecret,
            String tokenUri,
            String userInfoUri,
            String allowedRedirectUris
    ) {

        public Set<String> allowedRedirectUriSet() {
            if (allowedRedirectUris == null || allowedRedirectUris.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(allowedRedirectUris.split(","))
                    .map(String::trim)
                    .filter(uri -> !uri.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }

        public boolean allowsRedirectUri(String redirectUri) {
            return redirectUri != null && allowedRedirectUriSet().contains(redirectUri);
        }
    }
}
