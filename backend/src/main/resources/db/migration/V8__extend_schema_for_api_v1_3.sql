ALTER TABLE user_preferences ADD COLUMN push_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_preferences ADD COLUMN guardian_reaction_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_preferences ADD COLUMN screening_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_preferences ADD COLUMN diary_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_preferences ADD COLUMN weekly_report_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE recordings
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'answer';

ALTER TABLE recordings ALTER COLUMN session_id DROP NOT NULL;
ALTER TABLE recordings ALTER COLUMN question_id DROP NOT NULL;

ALTER TABLE recordings ADD CONSTRAINT ck_recordings_purpose CHECK (purpose IN ('answer', 'diary'));
ALTER TABLE recordings ADD CONSTRAINT ck_recordings_purpose_references CHECK (
    (purpose = 'answer' AND session_id IS NOT NULL AND question_id IS NOT NULL)
    OR (purpose = 'diary' AND session_id IS NULL AND question_id IS NULL)
);

ALTER TABLE game_results ADD COLUMN client_game_result_id UUID;
ALTER TABLE game_results ADD COLUMN matched_pairs INTEGER;
ALTER TABLE game_results ADD COLUMN attempt_count INTEGER;
ALTER TABLE game_results ADD COLUMN duration_sec INTEGER NOT NULL DEFAULT 0;
ALTER TABLE game_results ADD COLUMN restarted_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE game_results ADD COLUMN completed BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE game_results
SET client_game_result_id = id
WHERE client_game_result_id IS NULL;

ALTER TABLE game_results ALTER COLUMN client_game_result_id SET NOT NULL;

ALTER TABLE game_results ADD CONSTRAINT uq_game_results_client_id UNIQUE (client_game_result_id);
ALTER TABLE game_results ADD CONSTRAINT ck_game_results_matched_pairs CHECK (matched_pairs IS NULL OR matched_pairs >= 0);
ALTER TABLE game_results ADD CONSTRAINT ck_game_results_attempt_count CHECK (attempt_count IS NULL OR attempt_count >= 0);
ALTER TABLE game_results ADD CONSTRAINT ck_game_results_duration CHECK (duration_sec >= 0);
ALTER TABLE game_results ADD CONSTRAINT ck_game_results_restarted_count CHECK (restarted_count >= 0);
ALTER TABLE game_results ADD CONSTRAINT ck_game_results_image_match_metrics CHECK (
    game_type <> 'image_match'
    OR (matched_pairs IS NOT NULL AND attempt_count IS NOT NULL)
);

ALTER TABLE characters RENAME COLUMN xp_next TO xp_goal;
ALTER TABLE characters ADD COLUMN display_name VARCHAR(100) NOT NULL DEFAULT '꼬마 메모이';
ALTER TABLE characters ADD COLUMN stage VARCHAR(20) NOT NULL DEFAULT 'egg';
ALTER TABLE characters ADD CONSTRAINT ck_characters_stage CHECK (stage IN ('egg', 'puppy', 'sprout', 'flower', 'star'));

ALTER TABLE xp_ledger DROP CONSTRAINT ck_xp_ledger_reason;
UPDATE xp_ledger SET reason = 'emotional_qa' WHERE reason = 'chat';
ALTER TABLE xp_ledger
    ADD CONSTRAINT ck_xp_ledger_reason CHECK (reason IN ('attendance', 'visit', 'emotional_qa', 'campaign', 'game'));

CREATE TABLE daily_summaries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    local_date DATE NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    session_count INTEGER NOT NULL DEFAULT 0,
    analyzed_session_count INTEGER NOT NULL DEFAULT 0,
    analysis_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    summary TEXT,
    conversation_results JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_daily_summaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_daily_summaries_user_date UNIQUE (user_id, local_date, timezone),
    CONSTRAINT ck_daily_summaries_counts CHECK (
        session_count >= 0
        AND analyzed_session_count >= 0
        AND analyzed_session_count <= session_count
    ),
    CONSTRAINT ck_daily_summaries_status CHECK (analysis_status IN ('pending', 'processing', 'completed', 'failed'))
);

ALTER TABLE diaries
    ADD COLUMN daily_summary_id UUID;

ALTER TABLE diaries DROP CONSTRAINT ck_diaries_source_type;
ALTER TABLE diaries ADD CONSTRAINT fk_diaries_daily_summary FOREIGN KEY (daily_summary_id) REFERENCES daily_summaries (id) ON DELETE SET NULL;
ALTER TABLE diaries ADD CONSTRAINT uq_diaries_daily_summary UNIQUE (daily_summary_id);
ALTER TABLE diaries ADD CONSTRAINT ck_diaries_source_type CHECK (source_type IN ('manual', 'voice', 'session', 'daily_summary'));

CREATE TABLE diary_generation_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    daily_summary_id UUID,
    target_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'scheduled',
    scheduled_at TIMESTAMP WITH TIME ZONE,
    available_at TIMESTAMP WITH TIME ZONE,
    diary_id UUID,
    failure_reason VARCHAR(50),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_diary_generation_jobs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_diary_generation_jobs_summary FOREIGN KEY (daily_summary_id) REFERENCES daily_summaries (id) ON DELETE SET NULL,
    CONSTRAINT fk_diary_generation_jobs_diary FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE SET NULL,
    CONSTRAINT uq_diary_generation_jobs_user_date UNIQUE (user_id, target_date),
    CONSTRAINT uq_diary_generation_jobs_summary UNIQUE (daily_summary_id),
    CONSTRAINT ck_diary_generation_jobs_status CHECK (
        status IN ('scheduled', 'processing', 'completed', 'failed', 'conversation_incomplete')
    ),
    CONSTRAINT ck_diary_generation_jobs_failure CHECK (
        failure_reason IS NULL
        OR failure_reason IN ('summary_failed', 'generation_failed', 'insufficient_conversation', 'unknown')
    ),
    CONSTRAINT ck_diary_generation_jobs_retries CHECK (
        retry_count >= 0 AND max_retries >= 0 AND retry_count <= max_retries
    ),
    CONSTRAINT ck_diary_generation_jobs_completion CHECK (
        (status = 'completed' AND diary_id IS NOT NULL AND available_at IS NOT NULL)
        OR status <> 'completed'
    )
);

CREATE TABLE report_exports (
    id UUID PRIMARY KEY,
    guardian_id UUID NOT NULL,
    elder_id UUID NOT NULL,
    request_key VARCHAR(255) NOT NULL UNIQUE,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    format VARCHAR(10) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    status VARCHAR(20) NOT NULL DEFAULT 'processing',
    storage_key VARCHAR(500),
    expires_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(100),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_exports_guardian FOREIGN KEY (guardian_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_exports_elder FOREIGN KEY (elder_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_report_exports_dates CHECK (to_date >= from_date),
    CONSTRAINT ck_report_exports_format CHECK (format IN ('pdf', 'csv')),
    CONSTRAINT ck_report_exports_status CHECK (status IN ('processing', 'completed', 'failed')),
    CONSTRAINT ck_report_exports_completed CHECK (
        (status = 'completed' AND storage_key IS NOT NULL AND expires_at IS NOT NULL AND completed_at IS NOT NULL)
        OR status <> 'completed'
    )
);

ALTER TABLE notifications ADD COLUMN severity VARCHAR(20) NOT NULL DEFAULT 'info';
ALTER TABLE notifications ADD COLUMN status_label VARCHAR(100);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (
    type IN (
        'screening_alert', 'screening_updated', 'session_complete', 'summary',
        'diary_generated', 'diary_generation_failed', 'reminder', 'campaign',
        'weekly_report', 'guardian_reaction'
    )
);
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_severity CHECK (severity IN ('info', 'success', 'caution', 'danger'));

CREATE INDEX idx_daily_summaries_user_date ON daily_summaries (user_id, local_date DESC);
CREATE INDEX idx_diary_generation_jobs_user_status ON diary_generation_jobs (user_id, status, target_date DESC);
CREATE INDEX idx_report_exports_guardian_created ON report_exports (guardian_id, created_at DESC);
