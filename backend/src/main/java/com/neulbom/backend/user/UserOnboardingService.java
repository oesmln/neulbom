package com.neulbom.backend.user;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.game.CharacterEntity;
import com.neulbom.backend.game.CharacterRepository;
import com.neulbom.backend.user.api.ConsentRequest;
import com.neulbom.backend.user.api.ConsentResponse;
import com.neulbom.backend.user.api.ConsentsResponse;
import com.neulbom.backend.user.api.UserPreferenceResponse;
import com.neulbom.backend.user.api.UserPreferenceUpdateRequest;
import com.neulbom.backend.user.api.UserProfileResponse;
import com.neulbom.backend.user.api.UserProfileUpdateRequest;
import com.neulbom.backend.user.api.UserProfileUpdateResponse;
import com.neulbom.backend.user.api.VoiceProfileResponse;
import com.neulbom.backend.user.api.VoiceProfilesResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOnboardingService {

    private static final Set<String> AGE_GROUPS = Set.of("60s", "70s", "80s_plus", "unknown");
    private static final Set<String> GENDERS = Set.of("male", "female", "other", "unknown");
    private static final Set<String> ALCOHOL_USE = Set.of("none", "occasional", "frequent", "unknown");
    private static final Set<String> SMOKING_STATUS = Set.of("never", "former", "current", "unknown");
    private static final Set<String> HEARING_STATUS = Set.of("no_difficulty", "difficulty", "unknown");
    private static final Set<String> SMARTPHONE_SKILLS = Set.of("low", "medium", "high");
    private static final Set<String> HEARING_SIDES = Set.of("left", "right", "both", "unknown");
    private static final Set<String> ONBOARDING_STEPS = Set.of(
            "not_started", "intro", "character_name", "consent", "baseline", "completed");
    private static final Set<String> CONSENT_TYPES = Set.of(
            "terms_of_service", "privacy_collection", "sensitive_health", "report_sharing",
            "data_sharing", "guardian_access", "analysis", "voice_collection", "research_use");

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final VoiceProfileRepository voiceProfileRepository;
    private final ConsentRepository consentRepository;
    private final CharacterRepository characterRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    public UserOnboardingService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            UserPreferenceRepository userPreferenceRepository,
            VoiceProfileRepository voiceProfileRepository,
            ConsentRepository consentRepository,
            CharacterRepository characterRepository,
            ObjectMapper objectMapper,
            Clock clock,
            UuidGenerator uuidGenerator
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.voiceProfileRepository = voiceProfileRepository;
        this.consentRepository = consentRepository;
        this.characterRepository = characterRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID requestedUserId, UUID authenticatedUserId) {
        UserEntity user = requireSelfAndActive(requestedUserId, authenticatedUserId);
        UserProfileEntity profile = userProfileRepository.findById(user.getId()).orElse(null);
        return toProfileResponse(user, profile);
    }

    @Transactional
    public UserProfileUpdateResponse updateProfile(
            UUID requestedUserId,
            UUID authenticatedUserId,
            UserProfileUpdateRequest request
    ) {
        UserEntity user = requireSelfAndActive(requestedUserId, authenticatedUserId);
        validateProfileRequest(request);

        String name = request.name() == null ? user.getName() : normalizeRequired(request.name(), "name");
        LocalDate birthDate = request.birthDate() == null ? user.getBirthDate() : request.birthDate();
        String ageGroup = request.ageGroup() == null ? user.getAgeGroup() : request.ageGroup();
        String gender = request.gender() == null ? user.getGender() : request.gender();
        String phone = request.phone() == null ? user.getPhone() : normalizeOptional(request.phone());

        UserProfileEntity profile = userProfileRepository.findById(user.getId())
                .orElseGet(() -> new UserProfileEntity(user.getId()));
        Integer educationYears = request.educationYears() == null
                ? profile.getEducationYears() : request.educationYears();
        Boolean literacy = request.literacy() == null ? profile.getLiteracy() : request.literacy();
        String healthConditions = request.healthConditions() == null
                ? profile.getHealthConditions() : encodeHealthConditions(request.healthConditions());
        String alcoholUse = request.alcoholUse() == null ? profile.getAlcoholUse() : request.alcoholUse();
        String smokingStatus = request.smokingStatus() == null ? profile.getSmokingStatus() : request.smokingStatus();
        String hearingStatus = request.hearingStatus() == null ? profile.getHearingStatus() : request.hearingStatus();
        Boolean communicationDifficulty = request.communicationDifficulty() == null
                ? profile.getCommunicationDifficulty() : request.communicationDifficulty();
        String smartphoneSkill = request.smartphoneSkill() == null
                ? profile.getSmartphoneSkill() : request.smartphoneSkill();

        Instant now = clock.instant();
        profile.update(
                educationYears,
                literacy,
                healthConditions,
                alcoholUse,
                smokingStatus,
                hearingStatus,
                communicationDifficulty,
                smartphoneSkill);
        userProfileRepository.save(profile);
        user.updateProfile(name, birthDate, ageGroup, gender, phone, isProfileComplete(name, birthDate, ageGroup, gender), now);
        user.updateOnboarding(
                request.onboardingStep(),
                request.onboardingCompleted(),
                request.baselineCompleted(),
                request.characterName(),
                now);
        userRepository.save(user);
        syncCharacterName(user, request.characterName(), now);
        return new UserProfileUpdateResponse(
                user.getId(),
                user.isProfileCompleted(),
                user.getOnboardingStep(),
                user.isOnboardingCompleted(),
                user.isBaselineCompleted(),
                user.getCharacterName(),
                user.getUpdatedAt());
    }

    private void syncCharacterName(UserEntity user, String requestedName, Instant now) {
        if (requestedName == null) return;
        String normalized = normalizeOptional(requestedName);
        if (normalized == null) return;
        CharacterEntity character = characterRepository.findById(user.getId()).orElseGet(() ->
                new CharacterEntity(user.getId(), 1, normalized, "egg", 0, 100, null, "[]", now, now));
        character.rename(normalized, now);
        characterRepository.save(character);
    }

    @Transactional
    public UserPreferenceResponse getPreferences(UUID requestedUserId, UUID authenticatedUserId) {
        UserEntity user = requireSelfAndActive(requestedUserId, authenticatedUserId);
        UserPreferenceEntity preference = userPreferenceRepository.findById(user.getId())
                .orElseGet(() -> userPreferenceRepository.save(new UserPreferenceEntity(user.getId(), clock.instant())));
        return toPreferenceResponse(preference);
    }

    @Transactional
    public UserPreferenceResponse updatePreferences(
            UUID requestedUserId,
            UUID authenticatedUserId,
            UserPreferenceUpdateRequest request
    ) {
        UserEntity user = requireSelfAndActive(requestedUserId, authenticatedUserId);
        UserPreferenceEntity current = userPreferenceRepository.findById(user.getId())
                .orElseGet(() -> new UserPreferenceEntity(user.getId(), clock.instant()));

        String preferredHearingSide = request.preferredHearingSide() == null
                ? current.getPreferredHearingSide() : request.preferredHearingSide();
        validateEnum("preferred_hearing_side", preferredHearingSide, HEARING_SIDES);

        String voiceProfileId = current.getVoiceProfileId();
        if (request.voiceProfileId() != null) {
            voiceProfileId = normalizeOptional(request.voiceProfileId());
            if (voiceProfileId != null) {
                VoiceProfileEntity voiceProfile = voiceProfileRepository.findById(voiceProfileId)
                        .filter(VoiceProfileEntity::isActive)
                        .orElseThrow(() -> new ResourceNotFoundException("선택한 안내 음성을 찾을 수 없습니다."));
                if (!voiceProfile.getLanguage().equalsIgnoreCase("ko")) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "선택할 수 없는 안내 음성입니다.", "한국어 안내 음성만 사용할 수 있습니다.");
                }
            }
        }

        BigDecimal speechRate = request.speechRate() == null ? current.getSpeechRate() : request.speechRate();
        validateSpeechRate(speechRate);
        Instant now = clock.instant();
        current.update(
                preferredHearingSide,
                voiceProfileId,
                speechRate,
                valueOrCurrent(request.subtitleEnabled(), current.isSubtitleEnabled()),
                valueOrCurrent(request.soundEffectEnabled(), current.isSoundEffectEnabled()),
                valueOrCurrent(request.pushNotificationEnabled(), current.isPushNotificationEnabled()),
                valueOrCurrent(request.guardianReactionNotificationEnabled(), current.isGuardianReactionNotificationEnabled()),
                valueOrCurrent(request.screeningNotificationEnabled(), current.isScreeningNotificationEnabled()),
                valueOrCurrent(request.diaryNotificationEnabled(), current.isDiaryNotificationEnabled()),
                valueOrCurrent(request.weeklyReportNotificationEnabled(), current.isWeeklyReportNotificationEnabled()),
                now);
        userPreferenceRepository.save(current);
        return toPreferenceResponse(current);
    }

    @Transactional(readOnly = true)
    public VoiceProfilesResponse getVoiceProfiles(String language) {
        String normalizedLanguage = normalizeLanguage(language);
        List<VoiceProfileResponse> profiles = voiceProfileRepository
                .findAllByActiveTrueAndLanguageOrderByRecommendedForElderDescNameAsc(normalizedLanguage)
                .stream()
                .map(profile -> new VoiceProfileResponse(
                        profile.getId(),
                        profile.getName(),
                        profile.getPitchBand(),
                        profile.getClarity(),
                        profile.getPreviewAudioUrl(),
                        profile.isRecommendedForElder()))
                .toList();
        return new VoiceProfilesResponse(profiles);
    }

    @Transactional
    public ConsentResponse saveConsent(UUID requestedUserId, UUID authenticatedUserId, ConsentRequest request) {
        UserEntity user = requireSelfAndActive(requestedUserId, authenticatedUserId);
        validateConsentRequest(request);
        String consentType = request.consentType().trim();
        String version = request.version().trim();
        if (consentRepository.existsByUserIdAndConsentTypeAndVersion(user.getId(), consentType, version)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 저장된 동의입니다.", "동의 문서 버전을 확인하세요.");
        }

        Instant now = clock.instant();
        ConsentEntity consent = new ConsentEntity(
                uuidGenerator.generate(),
                user.getId(),
                consentType,
                request.agreed(),
                request.agreedAt(),
                version,
                now);
        try {
            consentRepository.save(consent);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 저장된 동의입니다.", "동의 문서 버전을 확인하세요.");
        }
        return toConsentResponse(consent);
    }

    @Transactional(readOnly = true)
    public ConsentsResponse getConsents(UUID requestedUserId, UUID authenticatedUserId) {
        UserEntity user = requireSelfAndActive(requestedUserId, authenticatedUserId);
        return new ConsentsResponse(consentRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toConsentResponse)
                .toList());
    }

    private UserEntity requireSelfAndActive(UUID requestedUserId, UUID authenticatedUserId) {
        if (!requestedUserId.equals(authenticatedUserId)) {
            throw new AccessDeniedException("본인 사용자 정보만 조회하거나 수정할 수 있습니다.");
        }
        return userRepository.findById(requestedUserId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
    }

    private void validateProfileRequest(UserProfileUpdateRequest request) {
        if (request.educationYears() != null && (request.educationYears() < 0 || request.educationYears() > 100)) {
            throw invalidField("education_years", "0~100 범위여야 합니다.");
        }
        if (request.birthDate() != null && request.birthDate().isAfter(LocalDate.now(clock))) {
            throw invalidField("birth_date", "오늘 이후 날짜를 사용할 수 없습니다.");
        }
        validateOptionalEnum("age_group", request.ageGroup(), AGE_GROUPS);
        validateOptionalEnum("gender", request.gender(), GENDERS);
        validateOptionalEnum("alcohol_use", request.alcoholUse(), ALCOHOL_USE);
        validateOptionalEnum("smoking_status", request.smokingStatus(), SMOKING_STATUS);
        validateOptionalEnum("hearing_status", request.hearingStatus(), HEARING_STATUS);
        validateOptionalEnum("smartphone_skill", request.smartphoneSkill(), SMARTPHONE_SKILLS);
        validateOptionalEnum("onboarding_step", request.onboardingStep(), ONBOARDING_STEPS);
        if (request.healthConditions() != null) {
            if (request.healthConditions().size() > 20 || request.healthConditions().stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 100)) {
                throw invalidField("health_conditions", "항목은 1~100자, 최대 20개까지 입력할 수 있습니다.");
            }
        }
    }

    private void validateConsentRequest(ConsentRequest request) {
        String consentType = request.consentType().trim();
        if (!CONSENT_TYPES.contains(consentType)) {
            throw invalidField("consent_type", "허용된 동의 유형이 아닙니다.");
        }
        if (request.version().isBlank()) {
            throw invalidField("version", "동의 문서 버전을 입력하세요.");
        }
        if (request.agreedAt().isAfter(clock.instant().plusSeconds(60))) {
            throw invalidField("agreed_at", "동의 일시는 현재 시각 이후일 수 없습니다.");
        }
    }

    private void validateOptionalEnum(String field, String value, Set<String> allowed) {
        if (value != null) {
            validateEnum(field, value, allowed);
        }
    }

    private void validateEnum(String field, String value, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw invalidField(field, "허용된 값이 아닙니다.");
        }
    }

    private void validateSpeechRate(BigDecimal speechRate) {
        if (speechRate == null || speechRate.compareTo(new BigDecimal("0.75")) < 0
                || speechRate.compareTo(new BigDecimal("1.25")) > 0) {
            throw invalidField("speech_rate", "0.75~1.25 범위여야 합니다.");
        }
    }

    private ApiException invalidField(String field, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", field + " " + detail);
    }

    private String normalizeLanguage(String language) {
        String normalized = language == null || language.isBlank() ? "ko" : language.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z]{2,10}")) {
            throw invalidField("language", "언어 코드는 영문으로 입력하세요.");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw invalidField(field, "비어 있을 수 없습니다.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isProfileComplete(String name, LocalDate birthDate, String ageGroup, String gender) {
        return name != null && !name.isBlank() && birthDate != null && ageGroup != null && gender != null;
    }

    private String encodeHealthConditions(List<String> healthConditions) {
        try {
            return objectMapper.writeValueAsString(healthConditions.stream().map(String::trim).toList());
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "건강 정보 저장에 실패했습니다.", "잠시 후 다시 시도하세요.");
        }
    }

    private List<String> decodeHealthConditions(String healthConditions) {
        if (healthConditions == null || healthConditions.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(healthConditions, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "건강 정보 조회에 실패했습니다.", "잠시 후 다시 시도하세요.");
        }
    }

    private UserProfileResponse toProfileResponse(UserEntity user, UserProfileEntity profile) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getRole(),
                user.getBirthDate(),
                user.getAgeGroup(),
                user.getGender(),
                user.getPhone(),
                profile == null ? null : profile.getEducationYears(),
                profile == null ? null : profile.getLiteracy(),
                profile == null ? List.of() : decodeHealthConditions(profile.getHealthConditions()),
                profile == null ? null : profile.getAlcoholUse(),
                profile == null ? null : profile.getSmokingStatus(),
                profile == null ? null : profile.getHearingStatus(),
                profile == null ? null : profile.getCommunicationDifficulty(),
                profile == null ? null : profile.getSmartphoneSkill(),
                user.isProfileCompleted(),
                user.getOnboardingStep(),
                user.isOnboardingCompleted(),
                user.isBaselineCompleted(),
                user.getCharacterName(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private UserPreferenceResponse toPreferenceResponse(UserPreferenceEntity preference) {
        return new UserPreferenceResponse(
                preference.getPreferredHearingSide(),
                preference.getVoiceProfileId(),
                preference.getSpeechRate(),
                preference.isSubtitleEnabled(),
                preference.isSoundEffectEnabled(),
                preference.isPushNotificationEnabled(),
                preference.isGuardianReactionNotificationEnabled(),
                preference.isScreeningNotificationEnabled(),
                preference.isDiaryNotificationEnabled(),
                preference.isWeeklyReportNotificationEnabled(),
                preference.getUpdatedAt());
    }

    private ConsentResponse toConsentResponse(ConsentEntity consent) {
        return new ConsentResponse(
                consent.getId(),
                consent.getConsentType(),
                consent.isAgreed(),
                consent.getAgreedAt(),
                consent.getVersion(),
                consent.getCreatedAt());
    }

    private boolean valueOrCurrent(Boolean value, boolean current) {
        return value == null ? current : value;
    }
}
