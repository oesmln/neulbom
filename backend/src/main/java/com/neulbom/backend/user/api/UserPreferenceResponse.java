package com.neulbom.backend.user.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserPreferenceResponse(
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
}
