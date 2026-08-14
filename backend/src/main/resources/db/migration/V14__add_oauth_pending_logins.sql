CREATE TABLE oauth_pending_logins (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320) NOT NULL,
    provider_display_name VARCHAR(100),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oauth_pending_logins_provider CHECK (provider IN ('kakao', 'naver')),
    CONSTRAINT ck_oauth_pending_logins_dates CHECK (expires_at > created_at)
);

CREATE INDEX idx_oauth_pending_logins_expires_at ON oauth_pending_logins (expires_at);
