ALTER TABLE cist_ai_analyses
    ADD COLUMN baseline_analysis_id UUID;

ALTER TABLE cist_ai_analyses
    ADD CONSTRAINT fk_cist_ai_analyses_baseline
        FOREIGN KEY (baseline_analysis_id)
        REFERENCES cist_ai_analyses (analysis_id);

CREATE INDEX idx_cist_ai_analyses_baseline
    ON cist_ai_analyses (baseline_analysis_id)
    WHERE baseline_analysis_id IS NOT NULL;
