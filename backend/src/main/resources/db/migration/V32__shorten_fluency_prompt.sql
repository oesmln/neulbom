-- 유창성 문항 안내에서 "준비되셨지요? 자, ... 시작!" 구간을 제거한다. 화면과 TTS가
-- 같은 내용을 쓰므로 content와 display_content를 함께 맞춘다. question_code와
-- variant_id는 그대로 두어 AI 계약에는 영향이 없다.
UPDATE questions SET
    content = '지금부터 제가 그만이라고 말할 때까지 과일이나 채소를 최대한 많이 이야기해 주세요.',
    display_content = '지금부터 제가 그만이라고 말할 때까지 과일이나 채소를 최대한 많이 이야기해 주세요.'
WHERE question_code = 'language_semantic_fluency';
