CREATE TABLE cist_recognition_plans (
    session_id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    submitted_recording_id UUID NOT NULL,
    submitted_response_id UUID NOT NULL,
    recalled_units JSONB,
    selected_question_codes JSONB,
    q11_result JSONB,
    reason_code VARCHAR(50),
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    retry_question_codes JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_cist_recognition_plans_session
        FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_cist_recognition_plans_recording
        FOREIGN KEY (submitted_recording_id) REFERENCES recordings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cist_recognition_plans_response
        FOREIGN KEY (submitted_response_id) REFERENCES answers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_cist_recognition_plans_status
        CHECK (status IN ('completed', 'needs_retry')),
    CONSTRAINT ck_cist_recognition_plans_attempt_count CHECK (attempt_count >= 0)
);

CREATE TABLE cist_ai_analyses (
    analysis_id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    create_idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    create_request_hash VARCHAR(64) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    reason_code VARCHAR(50),
    retry_items JSONB,
    submitted_responses JSONB NOT NULL,
    final_result JSONB,
    model_score NUMERIC(12, 10),
    model_version VARCHAR(120),
    decision_threshold NUMERIC(12, 10),
    threshold_version VARCHAR(120),
    risk_flag BOOLEAN,
    provider_created_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_cist_ai_analyses_session
        FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_cist_ai_analyses_status
        CHECK (status IN ('pending', 'processing', 'needs_retry', 'completed', 'failed')),
    CONSTRAINT ck_cist_ai_analyses_retry_count CHECK (retry_count >= 0)
);

CREATE TABLE ai_server_operations (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    analysis_id UUID,
    operation_type VARCHAR(30) NOT NULL,
    operation_number INTEGER NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    response_status VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_server_operations_session
        FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_server_operations_analysis
        FOREIGN KEY (analysis_id) REFERENCES cist_ai_analyses (analysis_id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_server_operation_number
        UNIQUE (session_id, operation_type, operation_number),
    CONSTRAINT ck_ai_server_operation_type
        CHECK (operation_type IN ('recognition_plan', 'analysis_create', 'analysis_retry')),
    CONSTRAINT ck_ai_server_operation_number CHECK (operation_number >= 0)
);

CREATE INDEX idx_cist_ai_analyses_status ON cist_ai_analyses (status, updated_at);
CREATE INDEX idx_ai_server_operations_analysis ON ai_server_operations (analysis_id, operation_number);
