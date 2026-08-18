package com.neulbom.backend.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CharacterEntityTest {

    @Test
    void cumulativeXpUsesFiveConfiguredLevelsAndStopsAtLevelFive() {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        CharacterEntity character = new CharacterEntity(
                UUID.randomUUID(), 1, "메모이", "egg", 0, 100, null, "[]", now, now);

        character.awardXp(99, now);
        assertProgress(character, 1, 99, 100, "egg");

        character.awardXp(1, now);
        assertProgress(character, 2, 100, 300, "puppy");

        character.awardXp(200, now);
        assertProgress(character, 3, 300, 600, "sprout");

        character.awardXp(300, now);
        assertProgress(character, 4, 600, 1_000, "flower");

        character.awardXp(400, now);
        assertProgress(character, 5, 1_000, 1_000, "star");

        character.awardXp(500, now);
        assertProgress(character, 5, 1_500, 1_000, "star");
    }

    private void assertProgress(CharacterEntity character, int level, int xp, int goal, String stage) {
        assertThat(character.getLevel()).isEqualTo(level);
        assertThat(character.getXpCurrent()).isEqualTo(xp);
        assertThat(character.getXpGoal()).isEqualTo(goal);
        assertThat(character.getStage()).isEqualTo(stage);
    }
}
