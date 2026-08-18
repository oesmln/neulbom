CREATE TABLE cist_question_rubrics (
    question_id UUID PRIMARY KEY,
    rule_type VARCHAR(40) NOT NULL,
    expected_values JSONB NOT NULL,
    max_score NUMERIC(8, 4) NOT NULL,
    rubric_version VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cist_question_rubrics_question
        FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    CONSTRAINT ck_cist_question_rubrics_max_score CHECK (max_score > 0)
);

CREATE TABLE cist_item_evaluations (
    id UUID PRIMARY KEY,
    answer_id UUID NOT NULL,
    session_id UUID NOT NULL,
    question_id UUID NOT NULL,
    category VARCHAR(20) NOT NULL,
    evaluation_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    is_correct BOOLEAN,
    score NUMERIC(8, 4),
    max_score NUMERIC(8, 4),
    response_time_ms INTEGER,
    explicit_wrong_event BOOLEAN NOT NULL DEFAULT FALSE,
    wrong_event_source VARCHAR(30),
    evaluator_version VARCHAR(50) NOT NULL,
    details JSONB,
    evaluated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cist_item_evaluations_answer
        FOREIGN KEY (answer_id) REFERENCES answers (id) ON DELETE CASCADE,
    CONSTRAINT fk_cist_item_evaluations_session
        FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_cist_item_evaluations_question
        FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_cist_item_evaluations_answer_version
        UNIQUE (answer_id, evaluator_version),
    CONSTRAINT ck_cist_item_evaluations_category
        CHECK (category IN ('orientation', 'memory', 'attention', 'language')),
    CONSTRAINT ck_cist_item_evaluations_status
        CHECK (evaluation_status IN ('pending', 'completed', 'unsupported', 'failed')),
    CONSTRAINT ck_cist_item_evaluations_response_time
        CHECK (response_time_ms IS NULL OR response_time_ms >= 0),
    CONSTRAINT ck_cist_item_evaluations_score
        CHECK (score IS NULL OR score >= 0),
    CONSTRAINT ck_cist_item_evaluations_max_score
        CHECK (max_score IS NULL OR max_score > 0)
);

CREATE TABLE cist_fusion_features (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    ast_score NUMERIC(8, 6),
    kc_electra_score NUMERIC(8, 6),
    category_balanced_wrong_event_score NUMERIC(8, 6) NOT NULL,
    category_balanced_median_delay NUMERIC(14, 6),
    feature_version VARCHAR(50) NOT NULL,
    scaler_version VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cist_fusion_features_session
        FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_cist_fusion_features_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_cist_fusion_features_ast_score
        CHECK (ast_score IS NULL OR ast_score BETWEEN 0 AND 1),
    CONSTRAINT ck_cist_fusion_features_kc_score
        CHECK (kc_electra_score IS NULL OR kc_electra_score BETWEEN 0 AND 1),
    CONSTRAINT ck_cist_fusion_features_wrong_score
        CHECK (category_balanced_wrong_event_score BETWEEN 0 AND 1),
    CONSTRAINT ck_cist_fusion_features_delay
        CHECK (category_balanced_median_delay IS NULL OR category_balanced_median_delay >= 0)
);

CREATE INDEX idx_cist_item_evaluations_session
    ON cist_item_evaluations (session_id, category);

CREATE INDEX idx_cist_item_evaluations_question
    ON cist_item_evaluations (question_id);
