package com.neulbom.backend.speech.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SpeechSynthesizeResponse(
        String audioContentBase64,
        String contentType,
        String voiceProfileId,
        String voiceName,
        BigDecimal speechRate
) {
}
