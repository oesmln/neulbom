CREATE TABLE guardian_links (
    id UUID PRIMARY KEY,
    guardian_id UUID NOT NULL,
    elder_id UUID NOT NULL,
    relation VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    consent_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guardian_links_guardian FOREIGN KEY (guardian_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_guardian_links_elder FOREIGN KEY (elder_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_guardian_links_pair UNIQUE (guardian_id, elder_id),
    CONSTRAINT ck_guardian_links_status CHECK (status IN ('pending', 'active', 'revoked')),
    CONSTRAINT ck_guardian_links_distinct_users CHECK (guardian_id <> elder_id)
);

CREATE TABLE guardian_link_scopes (
    link_id UUID NOT NULL,
    scope VARCHAR(20) NOT NULL,
    PRIMARY KEY (link_id, scope),
    CONSTRAINT fk_guardian_link_scopes_link FOREIGN KEY (link_id) REFERENCES guardian_links (id) ON DELETE CASCADE,
    CONSTRAINT ck_guardian_link_scopes_scope CHECK (scope IN ('screening', 'summary', 'diary', 'activity', 'campaign', 'all'))
);

CREATE TABLE guardian_invitations (
    id UUID PRIMARY KEY,
    guardian_id UUID NOT NULL,
    code_hash VARCHAR(255) NOT NULL UNIQUE,
    relation VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'issued',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    accepted_by_user_id UUID,
    used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guardian_invitations_guardian FOREIGN KEY (guardian_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_guardian_invitations_accepted_by FOREIGN KEY (accepted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_guardian_invitations_status CHECK (status IN ('issued', 'used', 'expired', 'revoked')),
    CONSTRAINT ck_guardian_invitations_attempts CHECK (attempt_count BETWEEN 0 AND max_attempts),
    CONSTRAINT ck_guardian_invitations_max_attempts CHECK (max_attempts BETWEEN 1 AND 20),
    CONSTRAINT ck_guardian_invitations_dates CHECK (expires_at > created_at)
);

CREATE TABLE guardian_invitation_scopes (
    invitation_id UUID NOT NULL,
    scope VARCHAR(20) NOT NULL,
    PRIMARY KEY (invitation_id, scope),
    CONSTRAINT fk_guardian_invitation_scopes_invitation FOREIGN KEY (invitation_id) REFERENCES guardian_invitations (id) ON DELETE CASCADE,
    CONSTRAINT ck_guardian_invitation_scopes_scope CHECK (scope IN ('screening', 'summary', 'diary', 'activity', 'campaign', 'all'))
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_type VARCHAR(20) NOT NULL DEFAULT 'cist',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    current_question_order INTEGER NOT NULL DEFAULT 1,
    answered_count INTEGER NOT NULL DEFAULT 0,
    total_questions INTEGER NOT NULL,
    settings JSONB,
    offline_mode BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_sessions_type CHECK (session_type IN ('cist', 'emotional_qa', 'game', 'mixed')),
    CONSTRAINT ck_sessions_status CHECK (status IN ('active', 'ended')),
    CONSTRAINT ck_sessions_question_counts CHECK (current_question_order > 0 AND answered_count >= 0 AND total_questions > 0 AND answered_count <= total_questions),
    CONSTRAINT ck_sessions_end_time CHECK (ended_at IS NULL OR ended_at >= started_at)
);
