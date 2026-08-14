package com.neulbom.backend.auth.api;

import com.neulbom.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(authService.register(request));
    }

    @GetMapping("/email/availability")
    public EmailAvailabilityResponse checkEmailAvailability(
            @RequestParam @NotBlank @Email String email
    ) {
        return authService.checkEmailAvailability(email);
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/email/verify/request")
    public ResponseEntity<EmailVerificationResponse> requestEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        return ResponseEntity.accepted().body(authService.requestEmailVerification(request));
    }

    @PostMapping("/email/verify/confirm")
    public ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationConfirmRequest request
    ) {
        authService.confirmEmailVerification(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth/{provider}")
    public AuthTokenResponse oauthLogin(
            @PathVariable String provider,
            @Valid @RequestBody OAuthLoginRequest request
    ) {
        return authService.loginWithOAuth(provider, request);
    }

    @PostMapping("/oauth/{provider}/prepare")
    public OAuthPrepareResponse prepareOAuthLogin(
            @PathVariable String provider,
            @Valid @RequestBody OAuthLoginRequest request
    ) {
        return authService.prepareOAuthLogin(provider, request);
    }

    @PostMapping("/oauth/{provider}/complete")
    public AuthTokenResponse completeOAuthLogin(
            @PathVariable String provider,
            @Valid @RequestBody OAuthCompleteRequest request
    ) {
        return authService.completeOAuthLogin(provider, request);
    }

    @PostMapping("/password/reset/request")
    public ResponseEntity<PasswordResetRequestResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.accepted().body(authService.requestPasswordReset(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/password/reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(authService.authenticatedUserId(jwt.getSubject()), request);
        return ResponseEntity.noContent().build();
    }
}
