ALTER TABLE cist_ai_analyses
    ADD COLUMN review_threshold NUMERIC(12, 10);

ALTER TABLE cist_ai_analyses
    ADD COLUMN risk_level VARCHAR(40);

ALTER TABLE cist_ai_analyses
    ADD CONSTRAINT ck_cist_ai_analyses_risk_level
    CHECK (
        risk_level IS NULL
        OR risk_level IN ('stable', 'monitoring_needed', 'review_needed')
    );
