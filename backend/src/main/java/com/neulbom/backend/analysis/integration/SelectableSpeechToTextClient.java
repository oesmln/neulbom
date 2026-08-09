package com.neulbom.backend.analysis.integration;

import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.ExternalApiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Selects the configured STT provider without changing the analysis service contract. */
@Primary
@Component
public class SelectableSpeechToTextClient implements SpeechToTextClient {

    private final OpenAiWhisperClient openAiWhisperClient;
    private final LocalWhisperClient localWhisperClient;
    private final GoogleCloudSpeechToTextClient googleCloudSpeechToTextClient;
    private final ExternalApiProperties properties;

    public SelectableSpeechToTextClient(
            @Qualifier("openAiWhisperClient") OpenAiWhisperClient openAiWhisperClient,
            @Qualifier("localWhisperClient") LocalWhisperClient localWhisperClient,
            @Qualifier("googleCloudSpeechToTextClient") GoogleCloudSpeechToTextClient googleCloudSpeechToTextClient,
            ExternalApiProperties properties
    ) {
        this.openAiWhisperClient = openAiWhisperClient;
        this.localWhisperClient = localWhisperClient;
        this.googleCloudSpeechToTextClient = googleCloudSpeechToTextClient;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        SpeechToTextClient selected = selectedClient();
        return selected != null && selected.isConfigured();
    }

    @Override
    public TranscriptionResult transcribe(AudioFile audioFile) {
        SpeechToTextClient selected = selectedClient();
        if (selected == null || !selected.isConfigured()) {
            throw new ExternalServiceUnavailableException(
                    "선택한 STT provider(" + properties.normalizedSttProvider() + ")가 설정되지 않았습니다.");
        }
        return selected.transcribe(audioFile);
    }

    private SpeechToTextClient selectedClient() {
        return switch (properties.normalizedSttProvider()) {
            case "openai", "whisper" -> openAiWhisperClient;
            case "local", "local-whisper" -> localWhisperClient;
            case "google", "google-stt", "cloud-stt" -> googleCloudSpeechToTextClient;
            case "none", "fallback", "disabled" -> null;
            case "auto" -> autoSelect();
            default -> throw new ExternalServiceUnavailableException(
                    "지원하지 않는 STT_PROVIDER 값입니다: " + properties.sttProvider());
        };
    }

    private SpeechToTextClient autoSelect() {
        if (openAiWhisperClient.isConfigured()) {
            return openAiWhisperClient;
        }
        if (localWhisperClient.isConfigured()) {
            return localWhisperClient;
        }
        if (googleCloudSpeechToTextClient.isConfigured()) {
            return googleCloudSpeechToTextClient;
        }
        return null;
    }
}
