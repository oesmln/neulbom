package com.neulbom.backend.campaign;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "campaign_participations")
public class CampaignParticipationEntity {

    @Id
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "reward_xp", nullable = false)
    private int rewardXp;
}
