package com.neulbom.backend.user;

import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.user.api.ConsentRequest;
import com.neulbom.backend.user.api.ConsentResponse;
import com.neulbom.backend.user.api.ConsentsResponse;
import com.neulbom.backend.user.api.UserPreferenceResponse;
import com.neulbom.backend.user.api.UserPreferenceUpdateRequest;
import com.neulbom.backend.user.api.UserProfileResponse;
import com.neulbom.backend.user.api.UserProfileUpdateRequest;
import com.neulbom.backend.user.api.UserProfileUpdateResponse;
import com.neulbom.backend.user.api.VoiceProfilesResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserOnboardingController {

    private final AuthService authService;
    private final UserOnboardingService onboardingService;

    public UserOnboardingController(AuthService authService, UserOnboardingService onboardingService) {
        this.authService = authService;
        this.onboardingService = onboardingService;
    }

    @GetMapping("/users/{userId}")
    public UserProfileResponse getProfile(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return onboardingService.getProfile(userId, authenticatedUserId(jwt));
    }

    @PatchMapping("/users/{userId}")
    public UserProfileUpdateResponse updateProfile(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        return onboardingService.updateProfile(userId, authenticatedUserId(jwt), request);
    }

    @GetMapping("/users/{userId}/preferences")
    public UserPreferenceResponse getPreferences(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return onboardingService.getPreferences(userId, authenticatedUserId(jwt));
    }

    @PatchMapping("/users/{userId}/preferences")
    public UserPreferenceResponse updatePreferences(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserPreferenceUpdateRequest request
    ) {
        return onboardingService.updatePreferences(userId, authenticatedUserId(jwt), request);
    }

    @GetMapping("/voice-profiles")
    public VoiceProfilesResponse getVoiceProfiles(@RequestParam(required = false, defaultValue = "ko") String language) {
        return onboardingService.getVoiceProfiles(language);
    }

    @PostMapping("/consent/{userId}")
    public ResponseEntity<ConsentResponse> saveConsent(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConsentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onboardingService.saveConsent(userId, authenticatedUserId(jwt), request));
    }

    @GetMapping("/consent/{userId}")
    public ConsentsResponse getConsents(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return onboardingService.getConsents(userId, authenticatedUserId(jwt));
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
