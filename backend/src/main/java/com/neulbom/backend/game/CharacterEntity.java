package com.neulbom.backend.game;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "characters")
public class CharacterEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private int level;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String stage;

    @Column(name = "xp_current", nullable = false)
    private int xpCurrent;

    @Column(name = "xp_goal", nullable = false)
    private int xpGoal;

    @Column(name = "skin_id", length = 100)
    private String skinId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String unlocked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CharacterEntity() {
    }

    public CharacterEntity(
            UUID userId,
            int level,
            String displayName,
            String stage,
            int xpCurrent,
            int xpGoal,
            String skinId,
            String unlocked,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.level = level;
        this.displayName = displayName;
        this.stage = stage;
        this.xpCurrent = xpCurrent;
        this.xpGoal = xpGoal;
        this.skinId = skinId;
        this.unlocked = unlocked;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public String getStage() { return stage; }
    public int getXpCurrent() { return xpCurrent; }
    public int getXpGoal() { return xpGoal; }
    public String getSkinId() { return skinId; }
    public String getUnlocked() { return unlocked; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public int awardXp(int amount, Instant updatedAt) {
        int previousLevel = level;
        xpCurrent += amount;
        while (xpCurrent >= xpGoal) {
            xpCurrent -= xpGoal;
            level++;
            xpGoal += 100;
        }
        stage = switch (Math.min(level, 5)) {
            case 1 -> "egg";
            case 2 -> "puppy";
            case 3 -> "sprout";
            case 4 -> "flower";
            default -> "star";
        };
        this.updatedAt = updatedAt;
        return level > previousLevel ? level - previousLevel : 0;
    }
}
