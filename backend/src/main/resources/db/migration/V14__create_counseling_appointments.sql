CREATE TABLE counseling_appointments (
    id UUID PRIMARY KEY,
    guardian_id UUID NOT NULL,
    elder_id UUID NOT NULL,
    center_id UUID NOT NULL,
    appointment_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consultation_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'requested',
    note VARCHAR(1000),
    privacy_agreed BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointments_guardian FOREIGN KEY (guardian_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_elder FOREIGN KEY (elder_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_center FOREIGN KEY (center_id) REFERENCES counseling_centers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_appointments_status CHECK (status IN ('requested', 'confirmed', 'cancelled', 'completed')),
    CONSTRAINT ck_appointments_consultation_type CHECK (consultation_type IN ('cognitive_screening', 'neurology', 'counseling', 'other')),
    CONSTRAINT ck_appointments_privacy_agreed CHECK (privacy_agreed = TRUE),
    CONSTRAINT ck_appointments_distinct_users CHECK (guardian_id <> elder_id)
);

CREATE INDEX idx_appointments_guardian_date ON counseling_appointments (guardian_id, appointment_at DESC);
CREATE INDEX idx_appointments_elder_date ON counseling_appointments (elder_id, appointment_at DESC);
