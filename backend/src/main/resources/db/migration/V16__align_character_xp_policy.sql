UPDATE characters
SET xp_current = xp_current + CASE
        WHEN level <= 1 THEN 0
        WHEN level = 2 THEN 100
        WHEN level = 3 THEN 300
        WHEN level = 4 THEN 600
        ELSE 50 * level * (level - 1)
    END,
    xp_goal = CASE
        WHEN level <= 1 THEN 100
        WHEN level = 2 THEN 300
        WHEN level = 3 THEN 600
        ELSE 1000
    END,
    level = CASE
        WHEN level < 1 THEN 1
        WHEN level > 5 THEN 5
        ELSE level
    END,
    stage = CASE
        WHEN level <= 1 THEN 'egg'
        WHEN level = 2 THEN 'puppy'
        WHEN level = 3 THEN 'sprout'
        WHEN level = 4 THEN 'flower'
        ELSE 'star'
    END;

ALTER TABLE characters DROP CONSTRAINT ck_characters_level;
ALTER TABLE characters
    ADD CONSTRAINT ck_characters_level CHECK (level BETWEEN 1 AND 5);

ALTER TABLE xp_ledger DROP CONSTRAINT ck_xp_ledger_amount;
ALTER TABLE xp_ledger
    ADD CONSTRAINT ck_xp_ledger_amount CHECK (amount >= 0);

ALTER TABLE xp_ledger DROP CONSTRAINT ck_xp_ledger_reason;
ALTER TABLE xp_ledger
    ADD CONSTRAINT ck_xp_ledger_reason
        CHECK (reason IN ('attendance', 'visit', 'emotional_qa', 'campaign', 'game', 'cist', 'streak'));
