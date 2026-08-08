package com.neulbom.backend.user.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserPreferenceUpdateRequest(
        String preferredHearingSide,
        @Size(max = 64) String voiceProfileId,
        @DecimalMin(value = "0.75") @DecimalMax(value = "1.25") BigDecimal speechRate,
        Boolean subtitleEnabled,
        Boolean soundEffectEnabled,
        Boolean pushNotificationEnabled,
        Boolean guardianReactionNotificationEnabled,
        Boolean screeningNotificationEnabled,
        Boolean diaryNotificationEnabled,
        Boolean weeklyReportNotificationEnabled
) {
}
