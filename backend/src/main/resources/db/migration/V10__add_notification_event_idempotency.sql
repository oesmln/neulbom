ALTER TABLE notifications ADD COLUMN event_key VARCHAR(255);

-- NULL event keys are allowed for manually created notifications. PostgreSQL and
-- H2 both permit multiple NULL values in a unique index, while non-null event
-- keys are unique per recipient.
CREATE UNIQUE INDEX uq_notifications_recipient_event_key
    ON notifications (recipient_user_id, event_key);
