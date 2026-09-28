ALTER TABLE game_results DROP CONSTRAINT ck_game_results_total_questions;

ALTER TABLE game_results ADD CONSTRAINT ck_game_results_total_questions
    CHECK (total_questions > 0 AND (game_type = 'image_match' OR error_count <= total_questions));
