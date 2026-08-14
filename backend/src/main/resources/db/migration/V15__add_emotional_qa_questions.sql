-- AI 정서 문답을 하루 5문항으로 제공한다.
-- V6의 기존 3문항은 유지하고, 새 문항만 추가해 이미 설치된 DB도 안전하게 보정한다.
INSERT INTO questions (
    id,
    question_type,
    session_type,
    content,
    hint,
    display_order,
    subtitle_available,
    active
) VALUES
    ('00000000-0000-0000-0000-000000000204', 'emotion', 'emotional_qa', '오늘 하루 중 가장 기뻤던 순간이 언제였나요?', NULL, 4, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000205', 'emotion', 'emotional_qa', '내일 기대되는 일이나 하고 싶은 것이 있으신가요?', NULL, 5, TRUE, TRUE);
