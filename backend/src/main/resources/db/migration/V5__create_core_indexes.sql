CREATE INDEX idx_refresh_tokens_user_expires
    ON refresh_tokens (user_id, expires_at);

CREATE INDEX idx_consents_user_type
    ON consents (user_id, consent_type, created_at DESC);

CREATE INDEX idx_guardian_links_guardian_status
    ON guardian_links (guardian_id, status);

CREATE INDEX idx_guardian_links_elder_status
    ON guardian_links (elder_id, status);

CREATE INDEX idx_guardian_link_scopes_scope
    ON guardian_link_scopes (scope, link_id);

CREATE INDEX idx_guardian_invitations_guardian_status
    ON guardian_invitations (guardian_id, status, created_at DESC);

CREATE INDEX idx_guardian_invitations_expires
    ON guardian_invitations (status, expires_at);

CREATE INDEX idx_guardian_invitation_scopes_scope
    ON guardian_invitation_scopes (scope, invitation_id);

CREATE INDEX idx_sessions_user_started
    ON sessions (user_id, started_at DESC);

CREATE INDEX idx_sessions_type_status
    ON sessions (session_type, status, started_at DESC);

CREATE INDEX idx_recordings_session_question
    ON recordings (session_id, question_id);

CREATE INDEX idx_recordings_status
    ON recordings (sync_status, transcript_status, analysis_status);

CREATE INDEX idx_answers_session_answered
    ON answers (session_id, answered_at);

CREATE INDEX idx_acoustic_analyses_recording_analyzed
    ON acoustic_analyses (recording_id, analyzed_at DESC);

CREATE INDEX idx_cognitive_analyses_user_analyzed
    ON cognitive_analyses (user_id, analyzed_at DESC);

CREATE INDEX idx_cognitive_analyses_session
    ON cognitive_analyses (session_id, analyzed_at DESC);

CREATE INDEX idx_screening_results_user_completed
    ON screening_results (user_id, completed_at DESC);

CREATE INDEX idx_session_summaries_user_created
    ON session_summaries (user_id, created_at DESC);

CREATE INDEX idx_diaries_user_written
    ON diaries (user_id, written_at DESC);

CREATE INDEX idx_diary_reactions_diary_created
    ON diary_reactions (diary_id, created_at DESC);

CREATE INDEX idx_game_results_user_played
    ON game_results (user_id, played_at DESC);

CREATE INDEX idx_xp_ledger_user_created
    ON xp_ledger (user_id, created_at DESC);

CREATE INDEX idx_campaigns_region_status_dates
    ON campaigns (region, status, start_date, end_date);

CREATE INDEX idx_campaign_participations_user_status
    ON campaign_participations (user_id, status, applied_at DESC);

CREATE INDEX idx_notifications_user_read_created
    ON notifications (recipient_user_id, is_read, created_at DESC);

CREATE INDEX idx_audit_logs_target_created
    ON audit_logs (target_user_id, created_at DESC);
