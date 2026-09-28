ALTER TABLE questions DROP CONSTRAINT uq_questions_session_order;

ALTER TABLE questions ADD COLUMN session_id UUID;
ALTER TABLE questions ADD COLUMN question_source VARCHAR(20);
ALTER TABLE questions ADD COLUMN source_question_id UUID;

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_session FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE;
ALTER TABLE questions
    ADD CONSTRAINT fk_questions_source_question FOREIGN KEY (source_question_id) REFERENCES questions (id) ON DELETE RESTRICT;
ALTER TABLE questions
    ADD CONSTRAINT ck_questions_source CHECK (question_source IS NULL OR question_source IN ('gemini', 'cist_bank'));

CREATE UNIQUE INDEX uq_questions_static_session_order
    ON questions (session_type, display_order)
    WHERE session_id IS NULL;
CREATE UNIQUE INDEX uq_questions_dynamic_session_order
    ON questions (session_id, display_order)
    WHERE session_id IS NOT NULL;

CREATE TABLE session_question_slots (
    session_id UUID NOT NULL,
    question_order INTEGER NOT NULL,
    question_source VARCHAR(20) NOT NULL,
    source_question_id UUID,
    question_id UUID UNIQUE,
    CONSTRAINT pk_session_question_slots PRIMARY KEY (session_id, question_order),
    CONSTRAINT fk_session_question_slots_session FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_session_question_slots_source FOREIGN KEY (source_question_id) REFERENCES questions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_question_slots_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_session_question_slots_order CHECK (question_order BETWEEN 1 AND 7),
    CONSTRAINT ck_session_question_slots_source CHECK (
        (question_source = 'gemini' AND source_question_id IS NULL)
        OR (question_source = 'cist_bank' AND source_question_id IS NOT NULL)
    )
);
