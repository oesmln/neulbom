CREATE TABLE cognitive_feature_snapshots (
    snapshot_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    source_session_id UUID NOT NULL REFERENCES sessions (id) ON DELETE RESTRICT,
    source_analysis_id UUID NOT NULL UNIQUE REFERENCES cist_ai_analyses (analysis_id) ON DELETE RESTRICT,
    question_set_version VARCHAR(120) NOT NULL,
    model_version VARCHAR(120) NOT NULL,
    threshold_version VARCHAR(120) NOT NULL,
    baseline_model_score NUMERIC(12, 10) NOT NULL,
    feature_snapshot JSONB NOT NULL,
    source_analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_cognitive_feature_snapshots_session UNIQUE (source_session_id),
    CONSTRAINT uq_cognitive_feature_snapshots_id_user UNIQUE (snapshot_id, user_id),
    CONSTRAINT ck_cognitive_feature_snapshot_score CHECK (baseline_model_score BETWEEN 0 AND 1)
);

CREATE INDEX idx_cognitive_feature_snapshots_user_analyzed
    ON cognitive_feature_snapshots (user_id, source_analyzed_at DESC, snapshot_id);

CREATE TABLE daily_cognitive_estimates (
    estimate_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    session_id UUID NOT NULL UNIQUE REFERENCES sessions (id) ON DELETE RESTRICT,
    baseline_snapshot_id UUID NOT NULL,
    parent_estimate_id UUID,
    status VARCHAR(20) NOT NULL,
    estimate_type VARCHAR(50) NOT NULL DEFAULT 'baseline_anchored_partial_update',
    estimated_model_score NUMERIC(12, 10),
    baseline_model_score NUMERIC(12, 10) NOT NULL,
    score_delta NUMERIC(12, 10),
    model_version VARCHAR(120),
    threshold_version VARCHAR(120),
    risk_level VARCHAR(40),
    result_json JSONB,
    output_feature_snapshot JSONB,
    analyzed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_daily_estimates_baseline_user
        FOREIGN KEY (baseline_snapshot_id, user_id)
        REFERENCES cognitive_feature_snapshots (snapshot_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT uq_daily_estimates_id_lineage
        UNIQUE (estimate_id, user_id, baseline_snapshot_id),
    CONSTRAINT fk_daily_estimates_parent_lineage
        FOREIGN KEY (parent_estimate_id, user_id, baseline_snapshot_id)
        REFERENCES daily_cognitive_estimates (estimate_id, user_id, baseline_snapshot_id) ON DELETE RESTRICT,
    CONSTRAINT ck_daily_estimates_status
        CHECK (status IN ('pending', 'processing', 'completed', 'failed')),
    CONSTRAINT ck_daily_estimates_type
        CHECK (estimate_type = 'baseline_anchored_partial_update'),
    CONSTRAINT ck_daily_estimates_score
        CHECK (estimated_model_score IS NULL OR estimated_model_score BETWEEN 0 AND 1),
    CONSTRAINT ck_daily_estimates_completed
        CHECK (status <> 'completed' OR
               (estimated_model_score IS NOT NULL AND result_json IS NOT NULL
                AND output_feature_snapshot IS NOT NULL AND analyzed_at IS NOT NULL))
);

CREATE INDEX idx_daily_estimates_user_analyzed
    ON daily_cognitive_estimates (user_id, analyzed_at DESC, estimate_id);
CREATE INDEX idx_daily_estimates_baseline_status_updated
    ON daily_cognitive_estimates (baseline_snapshot_id, status, updated_at DESC, estimate_id);
