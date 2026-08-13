ALTER TABLE game_results DROP CONSTRAINT ck_game_results_type;
ALTER TABLE game_results
    ADD CONSTRAINT ck_game_results_type
    CHECK (game_type IN ('image_match', 'consonant', 'word_match', 'color_match'));
