ALTER TABLE recordings
    ADD COLUMN duration_ms INTEGER;

ALTER TABLE recordings
    ADD CONSTRAINT ck_recordings_duration
        CHECK (duration_ms IS NULL OR duration_ms > 0);
