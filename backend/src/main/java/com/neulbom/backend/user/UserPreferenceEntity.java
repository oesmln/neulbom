package com.neulbom.backend.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_preferences")
public class UserPreferenceEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "preferred_hearing_side", nullable = false, length = 20)
    private String preferredHearingSide;

    @Column(name = "voice_profile_id", length = 64)
    private String voiceProfileId;

    @Column(name = "speech_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal speechRate;

    @Column(name = "subtitle_enabled", nullable = false)
    private boolean subtitleEnabled;

    @Column(name = "sound_effect_enabled", nullable = false)
    private boolean soundEffectEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
