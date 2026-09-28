ALTER TABLE ai_server_operations
    DROP CONSTRAINT ck_ai_server_operation_type;

ALTER TABLE ai_server_operations
    ADD CONSTRAINT ck_ai_server_operation_type
        CHECK (operation_type IN (
            'recognition_plan',
            'analysis_create',
            'analysis_retry',
            'daily_analysis_create',
            'daily_analysis_retry'
        ));
