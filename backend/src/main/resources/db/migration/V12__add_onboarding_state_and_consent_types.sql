ALTER TABLE users
    ADD COLUMN onboarding_step VARCHAR(30) NOT NULL DEFAULT 'not_started';

ALTER TABLE users
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN baseline_completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN character_name VARCHAR(100);

ALTER TABLE users
    ADD CONSTRAINT ck_users_onboarding_step
        CHECK (onboarding_step IN ('not_started', 'intro', 'character_name', 'consent', 'baseline', 'completed'));

ALTER TABLE consents
    DROP CONSTRAINT ck_consents_type;

ALTER TABLE consents
    ADD CONSTRAINT ck_consents_type CHECK (consent_type IN (
        'terms_of_service',
        'privacy_collection',
        'sensitive_health',
        'report_sharing',
        'data_sharing',
        'guardian_access',
        'analysis',
        'voice_collection',
        'research_use'
    ));

ALTER TABLE sessions
    DROP CONSTRAINT ck_sessions_type;

ALTER TABLE sessions
    ADD CONSTRAINT ck_sessions_type CHECK (session_type IN (
        'cist', 'baseline', 'onboarding', 'emotional_qa', 'game', 'mixed'
    ));

ALTER TABLE questions
    DROP CONSTRAINT ck_questions_session_type;

ALTER TABLE questions
    ADD CONSTRAINT ck_questions_session_type CHECK (session_type IN (
        'cist', 'baseline', 'onboarding', 'emotional_qa', 'game', 'mixed'
    ));
