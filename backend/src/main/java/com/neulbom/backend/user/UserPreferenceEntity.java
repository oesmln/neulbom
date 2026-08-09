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

    @Column(name = "push_notification_enabled", nullable = false)
    private boolean pushNotificationEnabled;

    @Column(name = "guardian_reaction_notification_enabled", nullable = false)
    private boolean guardianReactionNotificationEnabled;

    @Column(name = "screening_notification_enabled", nullable = false)
    private boolean screeningNotificationEnabled;

    @Column(name = "diary_notification_enabled", nullable = false)
    private boolean diaryNotificationEnabled;

    @Column(name = "weekly_report_notification_enabled", nullable = false)
    private boolean weeklyReportNotificationEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreferenceEntity() {
    }

    public UserPreferenceEntity(UUID userId, Instant now) {
        this.userId = userId;
        this.preferredHearingSide = "unknown";
        this.speechRate = new BigDecimal("0.90");
        this.subtitleEnabled = false;
        this.soundEffectEnabled = false;
        this.pushNotificationEnabled = true;
        this.guardianReactionNotificationEnabled = true;
        this.screeningNotificationEnabled = true;
        this.diaryNotificationEnabled = true;
        this.weeklyReportNotificationEnabled = true;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPreferredHearingSide() {
        return preferredHearingSide;
    }

    public String getVoiceProfileId() {
        return voiceProfileId;
    }

    public BigDecimal getSpeechRate() {
        return speechRate;
    }

    public boolean isSubtitleEnabled() {
        return subtitleEnabled;
    }

    public boolean isSoundEffectEnabled() {
        return soundEffectEnabled;
    }

    public boolean isPushNotificationEnabled() {
        return pushNotificationEnabled;
    }

    public boolean isGuardianReactionNotificationEnabled() {
        return guardianReactionNotificationEnabled;
    }

    public boolean isScreeningNotificationEnabled() {
        return screeningNotificationEnabled;
    }

    public boolean isDiaryNotificationEnabled() {
        return diaryNotificationEnabled;
    }

    public boolean isWeeklyReportNotificationEnabled() {
        return weeklyReportNotificationEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
            String preferredHearingSide,
            String voiceProfileId,
            BigDecimal speechRate,
            boolean subtitleEnabled,
            boolean soundEffectEnabled,
            boolean pushNotificationEnabled,
            boolean guardianReactionNotificationEnabled,
            boolean screeningNotificationEnabled,
            boolean diaryNotificationEnabled,
            boolean weeklyReportNotificationEnabled,
            Instant updatedAt
    ) {
        this.preferredHearingSide = preferredHearingSide;
        this.voiceProfileId = voiceProfileId;
        this.speechRate = speechRate;
        this.subtitleEnabled = subtitleEnabled;
        this.soundEffectEnabled = soundEffectEnabled;
        this.pushNotificationEnabled = pushNotificationEnabled;
        this.guardianReactionNotificationEnabled = guardianReactionNotificationEnabled;
        this.screeningNotificationEnabled = screeningNotificationEnabled;
        this.diaryNotificationEnabled = diaryNotificationEnabled;
        this.weeklyReportNotificationEnabled = weeklyReportNotificationEnabled;
        this.updatedAt = updatedAt;
    }
}
