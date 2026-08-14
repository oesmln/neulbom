package com.neulbom.backend.speech.integration;

import java.math.BigDecimal;

public interface TextToSpeechClient {

    boolean isConfigured();

    SynthesisResult synthesize(String text, String languageCode, String voiceName, BigDecimal speechRate);

    record SynthesisResult(
            byte[] audio,
            String contentType,
            String voiceName
    ) {
    }
}
