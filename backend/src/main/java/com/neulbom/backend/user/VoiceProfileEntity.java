package com.neulbom.backend.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "voice_profiles")
public class VoiceProfileEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "pitch_band", nullable = false, length = 20)
    private String pitchBand;

    @Column(nullable = false, length = 20)
    private String clarity;

    @Column(name = "preview_audio_url", length = 1000)
    private String previewAudioUrl;

    @Column(name = "recommended_for_elder", nullable = false)
    private boolean recommendedForElder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VoiceProfileEntity() {
    }

    public String getId() {
        return id;
    }

    public String getLanguage() {
        return language;
    }

    public String getName() {
        return name;
    }

    public String getPitchBand() {
        return pitchBand;
    }

    public String getClarity() {
        return clarity;
    }

    public String getPreviewAudioUrl() {
        return previewAudioUrl;
    }

    public boolean isRecommendedForElder() {
        return recommendedForElder;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
