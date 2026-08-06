package com.neulbom.backend.auth.oauth;

import com.neulbom.backend.auth.api.OAuthLoginRequest;

public interface OAuthProviderClient {

    String provider();

    OAuthProfile fetchProfile(OAuthLoginRequest request);
}
