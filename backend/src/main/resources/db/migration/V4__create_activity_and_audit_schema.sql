CREATE TABLE diaries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    title VARCHAR(200),
    content TEXT NOT NULL,
    recording_id UUID,
    session_id UUID,
    mood VARCHAR(20),
    mood_level INTEGER,
    written_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_diaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_diaries_recording FOREIGN KEY (recording_id) REFERENCES recordings (id) ON DELETE SET NULL,
    CONSTRAINT fk_diaries_session FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE SET NULL,
    CONSTRAINT ck_diaries_source_type CHECK (source_type IN ('manual', 'voice', 'session')),
    CONSTRAINT ck_diaries_mood CHECK (mood IS NULL OR mood IN ('very_sad', 'sad', 'neutral', 'happy', 'very_happy')),
    CONSTRAINT ck_diaries_mood_level CHECK (mood_level IS NULL OR mood_level BETWEEN 1 AND 5)
);

CREATE TABLE diary_reactions (
    id UUID PRIMARY KEY,
    diary_id UUID NOT NULL,
    reactor_id UUID NOT NULL,
    reaction_type VARCHAR(20) NOT NULL,
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_diary_reactions_diary FOREIGN KEY (diary_id) REFERENCES diaries (id) ON DELETE CASCADE,
    CONSTRAINT fk_diary_reactions_reactor FOREIGN KEY (reactor_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_diary_reactions_reactor_type UNIQUE (diary_id, reactor_id, reaction_type),
    CONSTRAINT ck_diary_reactions_type CHECK (reaction_type IN ('heart', 'smile', 'cheer', 'pray', 'cry', 'message')),
    CONSTRAINT ck_diary_reactions_message CHECK ((reaction_type = 'message' AND message IS NOT NULL) OR (reaction_type <> 'message'))
);

CREATE TABLE game_results (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    game_type VARCHAR(30) NOT NULL,
    score INTEGER NOT NULL,
    response_times JSONB NOT NULL,
    error_count INTEGER NOT NULL,
    total_questions INTEGER NOT NULL,
    cognitive_index NUMERIC(8, 2),
    played_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_game_results_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_results_session FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_game_results_type CHECK (game_type IN ('image_match', 'consonant', 'word_match')),
    CONSTRAINT ck_game_results_score CHECK (score >= 0),
    CONSTRAINT ck_game_results_error_count CHECK (error_count >= 0),
    CONSTRAINT ck_game_results_total_questions CHECK (total_questions > 0 AND error_count <= total_questions),
    CONSTRAINT ck_game_results_cognitive_index CHECK (cognitive_index IS NULL OR cognitive_index BETWEEN 0 AND 100)
);

CREATE TABLE characters (
    user_id UUID PRIMARY KEY,
    level INTEGER NOT NULL DEFAULT 1,
    xp_current INTEGER NOT NULL DEFAULT 0,
    xp_next INTEGER NOT NULL DEFAULT 100,
    skin_id VARCHAR(100),
    unlocked JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_characters_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_characters_level CHECK (level > 0),
    CONSTRAINT ck_characters_xp CHECK (xp_current >= 0 AND xp_next > 0)
);

CREATE TABLE xp_ledger (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_id VARCHAR(150) NOT NULL UNIQUE,
    amount INTEGER NOT NULL,
    reason VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_xp_ledger_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_xp_ledger_amount CHECK (amount > 0),
    CONSTRAINT ck_xp_ledger_reason CHECK (reason IN ('attendance', 'visit', 'chat', 'campaign', 'game'))
);

CREATE TABLE campaigns (
    id UUID PRIMARY KEY,
    region VARCHAR(100),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    reward_xp INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_campaigns_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_campaigns_status CHECK (status IN ('draft', 'active', 'ended', 'cancelled')),
    CONSTRAINT ck_campaigns_reward CHECK (reward_xp >= 0)
);

CREATE TABLE campaign_participations (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'applied',
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    reward_xp INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_campaign_participations_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_participations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_campaign_participations_user_campaign UNIQUE (campaign_id, user_id),
    CONSTRAINT ck_campaign_participations_status CHECK (status IN ('not_applied', 'applied', 'completed', 'cancelled')),
    CONSTRAINT ck_campaign_participations_reward CHECK (reward_xp >= 0)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(30) NOT NULL,
    data JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_type CHECK (type IN ('screening_alert', 'session_complete', 'summary', 'reminder', 'campaign', 'weekly_report', 'guardian_reaction')),
    CONSTRAINT ck_notifications_read_at CHECK ((is_read = TRUE AND read_at IS NOT NULL) OR (is_read = FALSE AND read_at IS NULL))
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    target_user_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id UUID,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_logs_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE SET NULL
);
