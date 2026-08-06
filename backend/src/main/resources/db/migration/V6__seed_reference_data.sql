INSERT INTO voice_profiles (
    id,
    language,
    name,
    pitch_band,
    clarity,
    preview_audio_url,
    recommended_for_elder,
    active
) VALUES
    ('voice_ko_01', 'ko', '기본 안내 음성', 'middle', 'normal', NULL, TRUE, TRUE),
    ('voice_ko_02', 'ko', '또렷한 안내 음성', 'middle', 'clear', NULL, TRUE, TRUE);

-- 개발용 질문 seed. 임상 적용 전 질문 문구와 채점 기준을 별도 검토한다.
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
    ('00000000-0000-0000-0000-000000000101', 'orientation', 'cist', '오늘은 몇 년도인지 말씀해 주세요.', NULL, 1, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000102', 'orientation', 'cist', '현재 계신 장소를 말씀해 주세요.', NULL, 2, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000103', 'memory', 'cist', '앞서 들은 단어를 기억해 보세요.', NULL, 3, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000104', 'attention', 'cist', '숫자를 순서대로 말해 주세요.', NULL, 4, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000105', 'language', 'cist', '그림 속 물건의 이름을 말해 주세요.', NULL, 5, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000201', 'emotion', 'emotional_qa', '오늘 기분을 말씀해 주세요.', NULL, 1, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000202', 'emotion', 'emotional_qa', '최근 즐거웠던 일을 이야기해 주세요.', NULL, 2, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000203', 'emotion', 'emotional_qa', '오늘 누군가와 이야기하고 싶은 주제가 있나요?', NULL, 3, TRUE, TRUE);
