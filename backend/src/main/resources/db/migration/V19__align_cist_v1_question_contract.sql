ALTER TABLE questions ADD COLUMN question_code VARCHAR(80);
ALTER TABLE questions ADD COLUMN variant_id VARCHAR(120);
ALTER TABLE questions ADD COLUMN administration_mode VARCHAR(20);

ALTER TABLE questions ADD CONSTRAINT uq_questions_question_code UNIQUE (question_code);
ALTER TABLE questions ADD CONSTRAINT ck_questions_administration_mode
    CHECK (administration_mode IS NULL OR administration_mode IN ('always', 'conditional'));

-- Preserve the stable IDs used by existing sessions while replacing the five
-- development placeholders with their matching cist-v1 contract questions.
UPDATE questions SET display_order = display_order + 100 WHERE session_type = 'cist';

UPDATE questions SET
    question_code = 'orientation_year',
    variant_id = 'orientation-year-fixed-v1',
    administration_mode = 'always',
    content = '올해는 몇 년도입니까?',
    display_order = 1
WHERE id = '00000000-0000-0000-0000-000000000101';

UPDATE questions SET
    question_code = 'orientation_place',
    variant_id = 'orientation-place-fixed-v1',
    administration_mode = 'always',
    content = '지금 대상자님이 계신 여기는 어디인가요? 이 장소가 어디인지 말씀해 주세요.',
    display_order = 5
WHERE id = '00000000-0000-0000-0000-000000000102';

UPDATE questions SET
    question_code = 'memory_delayed_free_recall',
    variant_id = 'memory-delayed-free-recall-fixed-v1',
    administration_mode = 'always',
    content = '제가 조금 전에 외우라고 불러드렸던 문장을 다시 한번 말씀해 주세요.',
    display_order = 11
WHERE id = '00000000-0000-0000-0000-000000000103';

UPDATE questions SET
    question_code = 'attention_digit_span_4',
    variant_id = 'attention-digit-span-4-fixed-v1',
    administration_mode = 'always',
    content = '제가 불러드리는 숫자를 그대로 따라 해 주세요: 6 - 9 - 7 - 3',
    display_order = 8
WHERE id = '00000000-0000-0000-0000-000000000104';

UPDATE questions SET
    question_code = 'language_semantic_fluency',
    variant_id = 'language-semantic-fluency-fixed-v1',
    administration_mode = 'always',
    content = '지금부터 제가 그만이라고 말할 때까지 과일이나 채소를 최대한 많이 이야기해 주세요. 준비되셨지요? 자, 과일이나 채소 이름을 말씀해 주세요. 시작!',
    display_order = 17
WHERE id = '00000000-0000-0000-0000-000000000105';

INSERT INTO questions (
    id, question_type, session_type, content, hint, display_order,
    subtitle_available, active, question_code, variant_id, administration_mode
) VALUES
    ('00000000-0000-0000-0000-000000000302', 'orientation', 'cist', '지금은 몇 월입니까?', NULL, 2, TRUE, TRUE, 'orientation_month', 'orientation-month-fixed-v1', 'always'),
    ('00000000-0000-0000-0000-000000000303', 'orientation', 'cist', '오늘은 며칠입니까?', NULL, 3, TRUE, TRUE, 'orientation_day', 'orientation-day-fixed-v1', 'always'),
    ('00000000-0000-0000-0000-000000000304', 'orientation', 'cist', '오늘은 무슨 요일입니까?', NULL, 4, TRUE, TRUE, 'orientation_weekday', 'orientation-weekday-fixed-v1', 'always'),
    ('00000000-0000-0000-0000-000000000306', 'memory', 'cist', '지금부터 외우셔야 하는 문장을 하나 불러 드리겠습니다. 끝까지 잘 듣고 따라 해 보세요: 민수는 자전거를 타고 공원에 가서 11시부터 야구를 했다', NULL, 6, TRUE, TRUE, 'memory_registration_first', 'memory-registration-first-fixed-v1', 'always'),
    ('00000000-0000-0000-0000-000000000307', 'memory', 'cist', '다시 한번 불러 드리겠습니다. 이번에도 다시 여쭈어 볼테니 잘 듣고 따라 해 보세요: 민수는 자전거를 타고 공원에 가서 11시부터 야구를 했다', NULL, 7, TRUE, TRUE, 'memory_registration_second', 'memory-registration-second-fixed-v1', 'always'),
    ('00000000-0000-0000-0000-000000000309', 'attention', 'cist', '제가 불러드리는 숫자를 그대로 따라 해 주세요: 5 - 7 - 2 - 8 - 4', NULL, 9, TRUE, TRUE, 'attention_digit_span_5', 'attention-digit-span-5-fixed-v1', 'always'),
    ('00000000-0000-0000-0000-000000000310', 'attention', 'cist', '제가 불러 드리는 말을 끝에서부터 거꾸로 따라해 주세요: 금수강산', NULL, 10, TRUE, TRUE, 'attention_word_reverse', 'attention-word-reverse-fixed-v1', 'always'),
    ('00000000-0000-0000-0000-000000000312', 'memory', 'cist', '제가 아까 어떤 사람의 이름을 말했는데 누구일까요? 영수, 민수, 진수', NULL, 12, TRUE, TRUE, 'memory_recognition_person', 'memory-recognition-person-fixed-v1', 'conditional'),
    ('00000000-0000-0000-0000-000000000313', 'memory', 'cist', '무엇을 타고 갔습니까? 버스, 오토바이, 자전거', NULL, 13, TRUE, TRUE, 'memory_recognition_transport', 'memory-recognition-transport-fixed-v1', 'conditional'),
    ('00000000-0000-0000-0000-000000000314', 'memory', 'cist', '어디에 갔습니까? 공원, 놀이터, 운동장', NULL, 14, TRUE, TRUE, 'memory_recognition_place', 'memory-recognition-place-fixed-v1', 'conditional'),
    ('00000000-0000-0000-0000-000000000315', 'memory', 'cist', '몇 시부터 했습니까? 10시, 11시, 12시', NULL, 15, TRUE, TRUE, 'memory_recognition_time', 'memory-recognition-time-fixed-v1', 'conditional'),
    ('00000000-0000-0000-0000-000000000316', 'memory', 'cist', '무엇을 했습니까? 농구, 축구, 야구', NULL, 16, TRUE, TRUE, 'memory_recognition_activity', 'memory-recognition-activity-fixed-v1', 'conditional');
