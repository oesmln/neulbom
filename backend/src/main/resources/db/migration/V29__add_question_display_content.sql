-- Separates the screen-facing prompt from the CIST stimulus (digits, sentence,
-- word) that must only be heard, not read, for attention/memory items whose
-- content embeds the answer stimulus after a colon.
ALTER TABLE questions ADD COLUMN display_content TEXT;

UPDATE questions SET
    display_content = '제가 불러드리는 숫자를 그대로 따라 해 주세요'
WHERE question_code = 'attention_digit_span_4';

UPDATE questions SET
    display_content = '제가 불러드리는 숫자를 그대로 따라 해 주세요'
WHERE question_code = 'attention_digit_span_5';

UPDATE questions SET
    display_content = '제가 불러 드리는 말을 끝에서부터 거꾸로 따라해 주세요'
WHERE question_code = 'attention_word_reverse';

UPDATE questions SET
    display_content = '지금부터 외우셔야 하는 문장을 하나 불러 드리겠습니다. 끝까지 잘 듣고 따라 해 보세요'
WHERE question_code = 'memory_registration_first';

UPDATE questions SET
    display_content = '다시 한번 불러 드리겠습니다. 이번에도 다시 여쭈어 볼테니 잘 듣고 따라 해 보세요'
WHERE question_code = 'memory_registration_second';
