ALTER TABLE characters DROP CONSTRAINT ck_characters_level;

UPDATE characters
SET level = CASE
        WHEN xp_current >= 1500 THEN 6
        WHEN xp_current >= 1000 THEN 5
        WHEN xp_current >= 600 THEN 4
        WHEN xp_current >= 300 THEN 3
        WHEN xp_current >= 100 THEN 2
        ELSE 1
    END,
    xp_goal = CASE
        WHEN xp_current < 100 THEN 100
        WHEN xp_current < 300 THEN 300
        WHEN xp_current < 600 THEN 600
        WHEN xp_current < 1000 THEN 1000
        ELSE 1500
    END,
    stage = CASE
        WHEN xp_current < 100 THEN 'egg'
        WHEN xp_current < 300 THEN 'puppy'
        WHEN xp_current < 600 THEN 'sprout'
        WHEN xp_current < 1000 THEN 'flower'
        ELSE 'star'
    END;

ALTER TABLE characters
    ADD CONSTRAINT ck_characters_level CHECK (level BETWEEN 1 AND 6);
