CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    birth_date DATE,
    age_group VARCHAR(20),
    gender VARCHAR(20),
    phone VARCHAR(30),
    profile_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_role CHECK (role IN ('elder', 'guardian')),
    CONSTRAINT ck_users_age_group CHECK (age_group IS NULL OR age_group IN ('60s', '70s', '80s_plus', 'unknown')),
    CONSTRAINT ck_users_gender CHECK (gender IS NULL OR gender IN ('male', 'female', 'other', 'unknown'))
);

CREATE TABLE voice_profiles (
    id VARCHAR(64) PRIMARY KEY,
    language VARCHAR(10) NOT NULL DEFAULT 'ko',
    name VARCHAR(100) NOT NULL,
    pitch_band VARCHAR(20) NOT NULL,
    clarity VARCHAR(20) NOT NULL,
    preview_audio_url VARCHAR(1000),
    recommended_for_elder BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_voice_profiles_pitch_band CHECK (pitch_band IN ('low', 'middle', 'high')),
    CONSTRAINT ck_voice_profiles_clarity CHECK (clarity IN ('normal', 'clear'))
);

CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY,
    education_years INTEGER,
    literacy BOOLEAN,
    health_conditions JSONB,
    alcohol_use VARCHAR(20),
    smoking_status VARCHAR(20),
    hearing_status VARCHAR(20),
    communication_difficulty BOOLEAN,
    smartphone_skill VARCHAR(20),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profiles_education_years CHECK (education_years IS NULL OR education_years BETWEEN 0 AND 100),
    CONSTRAINT ck_user_profiles_alcohol_use CHECK (alcohol_use IS NULL OR alcohol_use IN ('none', 'occasional', 'frequent', 'unknown')),
    CONSTRAINT ck_user_profiles_smoking_status CHECK (smoking_status IS NULL OR smoking_status IN ('never', 'former', 'current', 'unknown')),
    CONSTRAINT ck_user_profiles_hearing_status CHECK (hearing_status IS NULL OR hearing_status IN ('no_difficulty', 'difficulty', 'unknown')),
    CONSTRAINT ck_user_profiles_smartphone_skill CHECK (smartphone_skill IS NULL OR smartphone_skill IN ('low', 'medium', 'high'))
);

CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY,
    preferred_hearing_side VARCHAR(20) NOT NULL DEFAULT 'unknown',
    voice_profile_id VARCHAR(64),
    speech_rate NUMERIC(4, 2) NOT NULL DEFAULT 0.90,
    subtitle_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sound_effect_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_preferences_voice FOREIGN KEY (voice_profile_id) REFERENCES voice_profiles (id) ON DELETE SET NULL,
    CONSTRAINT ck_user_preferences_hearing_side CHECK (preferred_hearing_side IN ('left', 'right', 'both', 'unknown')),
    CONSTRAINT ck_user_preferences_speech_rate CHECK (speech_rate BETWEEN 0.75 AND 1.25)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE consents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    consent_type VARCHAR(30) NOT NULL,
    agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMP WITH TIME ZONE,
    version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_consents_user_type_version UNIQUE (user_id, consent_type, version),
    CONSTRAINT ck_consents_type CHECK (consent_type IN ('data_sharing', 'guardian_access', 'analysis', 'voice_collection', 'research_use'))
);

CREATE TABLE questions (
    id UUID PRIMARY KEY,
    question_type VARCHAR(20) NOT NULL,
    session_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    hint TEXT,
    display_order INTEGER NOT NULL,
    subtitle_available BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_questions_session_order UNIQUE (session_type, display_order),
    CONSTRAINT ck_questions_type CHECK (question_type IN ('orientation', 'memory', 'attention', 'language', 'emotion')),
    CONSTRAINT ck_questions_session_type CHECK (session_type IN ('cist', 'emotional_qa', 'game', 'mixed')),
    CONSTRAINT ck_questions_display_order CHECK (display_order > 0)
);
