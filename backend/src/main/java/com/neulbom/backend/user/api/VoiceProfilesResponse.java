package com.neulbom.backend.user.api;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VoiceProfilesResponse(List<VoiceProfileResponse> voiceProfiles) {
}
