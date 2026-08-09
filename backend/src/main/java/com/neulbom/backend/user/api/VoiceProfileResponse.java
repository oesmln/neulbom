package com.neulbom.backend.user.api;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VoiceProfileResponse(
        String voiceProfileId,
        String name,
        String pitchBand,
        String clarity,
        String previewAudioUrl,
        boolean recommendedForElder
) {
}
