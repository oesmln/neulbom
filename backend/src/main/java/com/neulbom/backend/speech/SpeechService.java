package com.neulbom.backend.speech;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.UUID;

import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.config.ExternalApiProperties;
import com.neulbom.backend.speech.api.SpeechSynthesizeRequest;
import com.neulbom.backend.speech.api.SpeechSynthesizeResponse;
import com.neulbom.backend.speech.integration.TextToSpeechClient;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserPreferenceEntity;
import com.neulbom.backend.user.UserPreferenceRepository;
import com.neulbom.backend.user.UserRepository;
import com.neulbom.backend.user.VoiceProfileEntity;
import com.neulbom.backend.user.VoiceProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SpeechService {

    private static final String DEFAULT_PROFILE_ID = "voice_ko_01";
    private static final String CLEAR_PROFILE_ID = "voice_ko_02";
    private static final BigDecimal DEFAULT_SPEECH_RATE = new BigDecimal("0.90");

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final VoiceProfileRepository voiceProfileRepository;
    private final TextToSpeechClient textToSpeechClient;
    private final ExternalApiProperties properties;

    public SpeechService(
            UserRepository userRepository,
            UserPreferenceRepository preferenceRepository,
            VoiceProfileRepository voiceProfileRepository,
            TextToSpeechClient textToSpeechClient,
            ExternalApiProperties properties
    ) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.voiceProfileRepository = voiceProfileRepository;
        this.textToSpeechClient = textToSpeechClient;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SpeechSynthesizeResponse synthesize(UUID authenticatedUserId, SpeechSynthesizeRequest request) {
        UserEntity user = userRepository.findById(authenticatedUserId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
        if (!"elder".equals(user.getRole())) {
            throw new AccessDeniedException("고령자 계정만 안내 음성을 사용할 수 있습니다.");
        }

        UserPreferenceEntity preference = preferenceRepository.findById(authenticatedUserId).orElse(null);
        String profileId = normalize(request.voiceProfileId());
        if (profileId == null && preference != null) {
            profileId = normalize(preference.getVoiceProfileId());
        }
        if (profileId == null) {
            profileId = DEFAULT_PROFILE_ID;
        }

        VoiceProfileEntity profile = voiceProfileRepository.findById(profileId)
                .filter(VoiceProfileEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("선택한 안내 음성을 찾을 수 없습니다."));
        if (!"ko".equalsIgnoreCase(profile.getLanguage())) {
            throw new AccessDeniedException("한국어 안내 음성만 사용할 수 있습니다.");
        }

        BigDecimal speechRate = request.speechRate() != null
                ? request.speechRate()
                : preference == null ? DEFAULT_SPEECH_RATE : preference.getSpeechRate();
        String voiceName = CLEAR_PROFILE_ID.equals(profileId)
                ? configured(properties.googleTtsClearVoice(), "ko-KR-Neural2-C")
                : configured(properties.googleTtsDefaultVoice(), "ko-KR-Neural2-A");
        String languageCode = configured(properties.googleTtsLanguageCode(), "ko-KR");

        if (!textToSpeechClient.isConfigured()) {
            throw new ExternalServiceUnavailableException("Google TTS 프로젝트와 ADC 인증을 설정해 주세요.");
        }
        TextToSpeechClient.SynthesisResult result = textToSpeechClient.synthesize(
                request.text().trim(), languageCode, voiceName, speechRate);
        return new SpeechSynthesizeResponse(
                Base64.getEncoder().encodeToString(result.audio()),
                result.contentType(),
                profileId,
                result.voiceName(),
                speechRate);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String configured(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
