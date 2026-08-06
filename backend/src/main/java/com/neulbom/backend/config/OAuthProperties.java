package com.neulbom.backend.config;

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
            String userInfoUri
    ) {
    }
}
