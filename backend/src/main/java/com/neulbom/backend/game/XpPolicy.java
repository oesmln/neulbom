package com.neulbom.backend.game;

/** Central character progression and reward policy shared by every XP source. */
public final class XpPolicy {

    public static final int MAX_LEVEL = 5;
    public static final int DAILY_XP_CAP = 100;

    public static final int GAME_PARTICIPATION_XP = 3;
    public static final int GAME_SUCCESS_XP = 10;
    public static final int EMOTIONAL_QA_XP = 20;
    public static final int FIRST_CIST_XP = 30;

    private static final int[] LEVEL_MINIMUMS = {0, 100, 300, 600, 1_000};

    private XpPolicy() {
    }

    public static int levelFor(int cumulativeXp) {
        int normalizedXp = Math.max(0, cumulativeXp);
        for (int index = LEVEL_MINIMUMS.length - 1; index >= 0; index--) {
            if (normalizedXp >= LEVEL_MINIMUMS[index]) {
                return index + 1;
            }
        }
        return 1;
    }

    public static int minimumForLevel(int level) {
        int normalizedLevel = Math.max(1, Math.min(MAX_LEVEL, level));
        return LEVEL_MINIMUMS[normalizedLevel - 1];
    }

    public static int goalForLevel(int level) {
        int normalizedLevel = Math.max(1, Math.min(MAX_LEVEL, level));
        if (normalizedLevel >= MAX_LEVEL) {
            return LEVEL_MINIMUMS[MAX_LEVEL - 1];
        }
        return LEVEL_MINIMUMS[normalizedLevel];
    }

    public static String stageForLevel(int level) {
        return switch (Math.max(1, Math.min(MAX_LEVEL, level))) {
            case 1 -> "egg";
            case 2 -> "puppy";
            case 3 -> "sprout";
            case 4 -> "flower";
            default -> "star";
        };
    }
}
