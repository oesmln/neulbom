CREATE TABLE push_devices (
    expo_push_token VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_push_devices_platform CHECK (platform IN ('ios', 'android'))
);

CREATE INDEX idx_push_devices_user ON push_devices (user_id);

CREATE TABLE expo_push_receipts (
    ticket_id VARCHAR(100) PRIMARY KEY,
    expo_push_token VARCHAR(255) NOT NULL REFERENCES push_devices (expo_push_token) ON DELETE CASCADE,
    notification_id UUID NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20)
);

CREATE INDEX idx_expo_push_receipts_pending ON expo_push_receipts (created_at) WHERE checked_at IS NULL;
