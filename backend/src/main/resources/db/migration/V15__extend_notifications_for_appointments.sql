ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (
    type IN (
        'screening_alert', 'screening_updated', 'session_complete', 'summary',
        'diary_generated', 'diary_generation_failed', 'reminder', 'campaign',
        'weekly_report', 'guardian_reaction', 'appointment_updated'
    )
);
