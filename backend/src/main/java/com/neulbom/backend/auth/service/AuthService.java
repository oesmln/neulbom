package com.neulbom.backend.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

import com.neulbom.backend.auth.api.AuthTokenResponse;
import com.neulbom.backend.auth.api.EmailAvailabilityResponse;
import com.neulbom.backend.auth.api.EmailVerificationConfirmRequest;
import com.neulbom.backend.auth.api.EmailVerificationRequest;
import com.neulbom.backend.auth.api.EmailVerificationResponse;
import com.neulbom.backend.auth.api.LoginRequest;
import com.neulbom.backend.auth.api.OAuthLoginRequest;
import com.neulbom.backend.auth.api.OAuthCompleteRequest;
import com.neulbom.backend.auth.api.OAuthPrepareResponse;
import com.neulbom.backend.auth.api.PasswordResetConfirmRequest;
import com.neulbom.backend.auth.api.PasswordResetRequest;
import com.neulbom.backend.auth.api.PasswordResetRequestResponse;
import com.neulbom.backend.auth.api.PasswordChangeRequest;
import com.neulbom.backend.auth.api.RefreshRequest;
import com.neulbom.backend.auth.api.RegisterRequest;
import com.neulbom.backend.auth.api.RegisterResponse;
import com.neulbom.backend.auth.api.LogoutRequest;
import com.neulbom.backend.auth.oauth.OAuthProfile;
import com.neulbom.backend.auth.oauth.OAuthProviderClientRegistry;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.audit.AuditLogEntity;
import com.neulbom.backend.common.audit.AuditLogRepository;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.user.OAuthAccountEntity;
import com.neulbom.backend.user.OAuthAccountRepository;
import com.neulbom.backend.user.OAuthPendingLoginEntity;
import com.neulbom.backend.user.OAuthPendingLoginRepository;
import com.neulbom.backend.user.EmailVerificationTokenEntity;
import com.neulbom.backend.user.EmailVerificationTokenRepository;
import com.neulbom.backend.user.PasswordResetTokenEntity;
import com.neulbom.backend.user.PasswordResetTokenRepository;
import com.neulbom.backend.user.RefreshTokenEntity;
import com.neulbom.backend.user.RefreshTokenRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(15);
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final UuidGenerator uuidGenerator;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final JwtTokenService jwtTokenService;
    private final PasswordResetNotifier passwordResetNotifier;
    private final OAuthProviderClientRegistry oauthProviderClientRegistry;
    private final OAuthPendingLoginRepository oauthPendingLoginRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    private final EmailVerificationNotifier emailVerificationNotifier;
    private final boolean emailVerificationRequired;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            OAuthAccountRepository oauthAccountRepository,
            PasswordEncoder passwordEncoder,
            UuidGenerator uuidGenerator,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            JwtTokenService jwtTokenService,
            PasswordResetNotifier passwordResetNotifier,
            OAuthProviderClientRegistry oauthProviderClientRegistry,
            OAuthPendingLoginRepository oauthPendingLoginRepository,
            AuditLogRepository auditLogRepository,
            PasswordResetRateLimiter passwordResetRateLimiter,
            EmailVerificationNotifier emailVerificationNotifier,
            @Value("${app.security.email-verification-required:false}") boolean emailVerificationRequired
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.uuidGenerator = uuidGenerator;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.jwtTokenService = jwtTokenService;
        this.passwordResetNotifier = passwordResetNotifier;
        this.oauthProviderClientRegistry = oauthProviderClientRegistry;
        this.oauthPendingLoginRepository = oauthPendingLoginRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordResetRateLimiter = passwordResetRateLimiter;
        this.emailVerificationNotifier = emailVerificationNotifier;
        this.emailVerificationRequired = emailVerificationRequired;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.", "로그인해 주세요.");
        }

        Instant now = jwtTokenService.now();
        UserEntity user = new UserEntity(
                uuidGenerator.generate(),
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                request.role(),
                request.birthDate(),
                request.ageGroup(),
                request.gender(),
                request.phone(),
                false,
                now,
                now);
        if (emailVerificationRequired) {
            user.markEmailUnverified();
        }
        userRepository.save(user);
        if (emailVerificationRequired) {
            issueEmailVerification(user);
        }
        return new RegisterResponse(
                user.getId(),
                user.getRole(),
                user.isProfileCompleted(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public EmailAvailabilityResponse checkEmailAvailability(String requestedEmail) {
        String email = normalizeEmail(requestedEmail);
        return new EmailAvailabilityResponse(email, !userRepository.existsByEmailIgnoreCase(email));
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (!user.isActive() || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        requireVerifiedEmail(user);
        return issueTokens(user, false);
    }

    @Transactional
    public EmailVerificationResponse requestEmailVerification(EmailVerificationRequest request) {
        Instant now = jwtTokenService.now();
        Instant expiresAt = now.plus(EMAIL_VERIFICATION_TTL);
        UUID requestId = uuidGenerator.generate();
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        if (user == null || !user.isActive() || user.isEmailVerified()) {
            return new EmailVerificationResponse(requestId, expiresAt);
        }

        emailVerificationTokenRepository.findAllByUserIdAndUsedAtIsNull(user.getId())
                .forEach(token -> token.markUsed(now));
        String rawToken = tokenGenerator.generate();
        emailVerificationTokenRepository.save(new EmailVerificationTokenEntity(
                requestId,
                user.getId(),
                tokenHasher.hash(rawToken),
                expiresAt,
                now));
        emailVerificationNotifier.send(user.getEmail(), rawToken, expiresAt);
        return new EmailVerificationResponse(requestId, expiresAt);
    }

    @Transactional
    public void confirmEmailVerification(EmailVerificationConfirmRequest request) {
        Instant now = jwtTokenService.now();
        EmailVerificationTokenEntity verificationToken = emailVerificationTokenRepository
                .findByTokenHash(tokenHasher.hash(request.verificationToken()))
                .filter(token -> token.isUsable(now))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.GONE,
                        "이메일 인증 token이 만료되었거나 이미 사용되었습니다.",
                        "새로운 인증 메일을 요청하세요."));
        UserEntity user = userRepository.findById(verificationToken.getUserId()).orElseThrow(this::invalidCredentials);
        if (!user.isActive()) {
            throw invalidCredentials();
        }
        user.verifyEmail(now);
        verificationToken.markUsed(now);
    }

    @Transactional
    public AuthTokenResponse loginWithOAuth(String provider, OAuthLoginRequest request) {
        String normalizedProvider = normalizeProvider(provider);
        OAuthProfile profile = fetchAndValidateOAuthProfile(normalizedProvider, request);
        Instant now = jwtTokenService.now();
        ExistingOAuthAccount existing = findExistingOAuthAccount(profile, now);
        if (existing != null) {
            oauthAccountRepository.save(existing.oauthAccount());
            return issueTokens(existing.user(), false);
        }
        requireEmailForNewOAuthAccount(profile);
        if (request.role() == null || request.role().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "가입 역할이 필요합니다.", "신규 소셜 계정은 role을 함께 보내야 합니다.");
        }
        return createOAuthUser(profile, request.role(), now);
    }

    /**
     * Exchanges the provider authorization code before asking a first-time
     * social user for a role. Existing accounts receive normal app tokens;
     * new accounts receive a one-time pending token instead.
     */
    @Transactional
    public OAuthPrepareResponse prepareOAuthLogin(String provider, OAuthLoginRequest request) {
        String normalizedProvider = normalizeProvider(provider);
        OAuthProfile profile = fetchAndValidateOAuthProfile(normalizedProvider, request);
        Instant now = jwtTokenService.now();
        ExistingOAuthAccount existing = findExistingOAuthAccount(profile, now);
        if (existing != null) {
            oauthAccountRepository.save(existing.oauthAccount());
            return OAuthPrepareResponse.authenticated(issueTokens(existing.user(), false));
        }
        requireEmailForNewOAuthAccount(profile);

        String pendingToken = tokenGenerator.generate();
        oauthPendingLoginRepository.save(new OAuthPendingLoginEntity(
                uuidGenerator.generate(),
                tokenHasher.hash(pendingToken),
                profile.provider(),
                profile.providerUserId(),
                normalizeEmail(profile.email()),
                profile.displayName(),
                now.plus(Duration.ofMinutes(10)),
                now));
        return OAuthPrepareResponse.roleRequired(pendingToken, normalizeEmail(profile.email()), profile.displayName());
    }

    /** Completes a first-time social login after the user chooses a role. */
    @Transactional
    public AuthTokenResponse completeOAuthLogin(String provider, OAuthCompleteRequest request) {
        String normalizedProvider = normalizeProvider(provider);
        Instant now = jwtTokenService.now();
        OAuthPendingLoginEntity pending = oauthPendingLoginRepository
                .findByTokenHash(tokenHasher.hash(request.pendingToken()))
                .filter(candidate -> normalizedProvider.equals(candidate.getProvider()))
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.GONE,
                        "소셜 로그인 요청이 만료되었어요.",
                        "처음부터 소셜 로그인을 다시 시도해 주세요."));
        OAuthProfile profile = new OAuthProfile(
                pending.getProvider(),
                pending.getProviderUserId(),
                pending.getProviderEmail(),
                pending.getProviderDisplayName(),
                true);
        ExistingOAuthAccount existing = findExistingOAuthAccount(profile, now);
        if (existing != null) {
            pending.markConsumed(now);
            oauthPendingLoginRepository.save(pending);
            oauthAccountRepository.save(existing.oauthAccount());
            return issueTokens(existing.user(), false);
        }

        AuthTokenResponse tokens = createOAuthUser(profile, request.role(), now);
        pending.markConsumed(now);
        oauthPendingLoginRepository.save(pending);
        return tokens;
    }

    @Transactional
    public PasswordResetRequestResponse requestPasswordReset(PasswordResetRequest request, String clientIp) {
        passwordResetRateLimiter.check(request.email(), clientIp);
        Instant now = jwtTokenService.now();
        Instant expiresAt = now.plus(PASSWORD_RESET_TTL);
        UUID requestId = uuidGenerator.generate();
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        if (user == null || !user.isActive() || user.getPasswordHash() == null) {
            return new PasswordResetRequestResponse(requestId, expiresAt);
        }

        passwordResetTokenRepository.findAllByUserIdAndUsedAtIsNull(user.getId())
                .forEach(token -> token.markUsed(now));
        String rawToken = tokenGenerator.generate();
        passwordResetTokenRepository.save(new PasswordResetTokenEntity(
                requestId,
                user.getId(),
                tokenHasher.hash(rawToken),
                expiresAt,
                now));
        passwordResetNotifier.send(user.getEmail(), rawToken, expiresAt);
        return new PasswordResetRequestResponse(requestId, expiresAt);
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        Instant now = jwtTokenService.now();
        PasswordResetTokenEntity resetToken = passwordResetTokenRepository.findByTokenHash(
                        tokenHasher.hash(request.resetToken()))
                .filter(token -> token.isUsable(now))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.GONE, "비밀번호 재설정 token이 만료되었거나 이미 사용되었습니다.", "새로운 재설정 요청을 생성하세요."));
        UserEntity user = userRepository.findById(resetToken.getUserId()).orElseThrow(this::invalidCredentials);
        if (!user.isActive()) {
            throw invalidCredentials();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        resetToken.markUsed(now);
        revokeAllRefreshTokens(user.getId(), now);
    }

    @Transactional
    public void changePassword(UUID authenticatedUserId, PasswordChangeRequest request) {
        UserEntity user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
        if (!user.isActive() || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.", "현재 비밀번호를 확인하세요.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "현재 비밀번호와 다른 비밀번호를 사용하세요.", "새 비밀번호를 변경하세요.");
        }

        Instant now = jwtTokenService.now();
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        if (request.shouldLogoutOtherSessions()) {
            revokeAllRefreshTokens(user.getId(), now);
        }
        auditLogRepository.save(new AuditLogEntity(
                uuidGenerator.generate(),
                user.getId(),
                user.getId(),
                "password_changed",
                "user",
                user.getId(),
                "{\"refresh_tokens_revoked\":" + request.shouldLogoutOtherSessions() + "}",
                now));
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshRequest request) {
        Instant now = jwtTokenService.now();
        RefreshTokenEntity oldToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(request.refreshToken()))
                .filter(token -> token.isUsable(now))
                .orElseThrow(this::invalidCredentials);
        UserEntity user = userRepository.findById(oldToken.getUserId()).orElseThrow(this::invalidCredentials);
        if (!user.isActive()) {
            throw invalidCredentials();
        }
        requireVerifiedEmail(user);
        oldToken.markUsed(now);
        oldToken.revoke(now);
        return issueTokens(user, false);
    }

    @Transactional
    public void logout(UUID authenticatedUserId, LogoutRequest request) {
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(request.refreshToken()))
                .orElse(null);
        if (token == null) {
            return;
        }
        if (!token.getUserId().equals(authenticatedUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "토큰을 폐기할 권한이 없습니다.", "본인의 refresh token만 폐기할 수 있습니다.");
        }
        token.revoke(jwtTokenService.now());
    }

    @Transactional
    public void withdraw(UUID authenticatedUserId) {
        UserEntity user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
        if (!user.isActive()) {
            return;
        }
        Instant now = jwtTokenService.now();
        user.withdraw(now);
        revokeAllRefreshTokens(user.getId(), now);
        passwordResetTokenRepository.findAllByUserIdAndUsedAtIsNull(user.getId())
                .forEach(token -> token.markUsed(now));
        oauthAccountRepository.deleteAllByUserId(user.getId());
    }

    public UUID authenticatedUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw invalidCredentials();
        }
    }

    private OAuthProfile fetchAndValidateOAuthProfile(String normalizedProvider, OAuthLoginRequest request) {
        OAuthProfile profile = oauthProviderClientRegistry.clientFor(normalizedProvider).fetchProfile(request);
        if (!normalizedProvider.equals(profile.provider())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다.", "소셜 provider 정보가 일치하지 않습니다.");
        }
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다.", "소셜 provider 사용자 식별자를 확인할 수 없습니다.");
        }
        return profile;
    }

    /**
     * A provider may omit the optional/declined email field even when the
     * provider account is already linked. Existing OAuth accounts are keyed
     * by the provider user id, so they can still sign in without re-linking or
     * losing the email stored on our user record. Only a new social account
     * needs a provider email because users.email is required and unique.
     */
    private void requireEmailForNewOAuthAccount(OAuthProfile profile) {
        if (!profile.emailVerified() || profile.email() == null || profile.email().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다.", "검증된 이메일을 제공하는 계정만 사용할 수 있습니다.");
        }
    }

    private ExistingOAuthAccount findExistingOAuthAccount(OAuthProfile profile, Instant now) {
        OAuthAccountEntity oauthAccount = oauthAccountRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .orElse(null);
        if (oauthAccount != null) {
            UserEntity user = userRepository.findById(oauthAccount.getUserId()).orElseThrow(this::invalidCredentials);
            if (!user.isActive()) {
                throw invalidCredentials();
            }
            oauthAccount.updateProfile(profile.email(), profile.displayName(), now);
            return new ExistingOAuthAccount(user, oauthAccount);
        }

        if (profile.email() == null || profile.email().isBlank()) {
            return null;
        }

        UserEntity user = userRepository.findByEmailIgnoreCase(normalizeEmail(profile.email())).orElse(null);
        if (user == null) {
            return null;
        }
        if (!user.isActive()) {
            throw invalidCredentials();
        }
        return new ExistingOAuthAccount(user, new OAuthAccountEntity(
                uuidGenerator.generate(),
                user.getId(),
                profile.provider(),
                profile.providerUserId(),
                normalizeEmail(profile.email()),
                profile.displayName(),
                now,
                now));
    }

    private AuthTokenResponse createOAuthUser(OAuthProfile profile, String role, Instant now) {
        UserEntity user = new UserEntity(
                uuidGenerator.generate(),
                normalizeEmail(profile.email()),
                null,
                profile.displayName() == null || profile.displayName().isBlank()
                        ? "소셜 사용자" : profile.displayName(),
                role,
                null,
                null,
                null,
                null,
                false,
                now,
                now);
        userRepository.save(user);
        oauthAccountRepository.save(new OAuthAccountEntity(
                uuidGenerator.generate(),
                user.getId(),
                profile.provider(),
                profile.providerUserId(),
                normalizeEmail(profile.email()),
                profile.displayName(),
                now,
                now));
        return issueTokens(user, true);
    }

    private String normalizeProvider(String provider) {
        return provider.toLowerCase(Locale.ROOT);
    }

    private record ExistingOAuthAccount(UserEntity user, OAuthAccountEntity oauthAccount) {
    }

    private AuthTokenResponse issueTokens(UserEntity user, boolean newUser) {
        String accessToken = jwtTokenService.issueAccessToken(user);
        String refreshToken = tokenGenerator.generate();
        Instant now = jwtTokenService.now();
        refreshTokenRepository.save(new RefreshTokenEntity(
                uuidGenerator.generate(),
                user.getId(),
                tokenHasher.hash(refreshToken),
                jwtTokenService.refreshTokenExpiresAt(),
                now));
        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                jwtTokenService.accessTokenExpiresInSeconds(),
                user.getId(),
                user.getRole(),
                user.isProfileCompleted(),
                user.isEmailVerified(),
                newUser,
                user.getOnboardingStep(),
                user.isOnboardingCompleted(),
                user.isBaselineCompleted(),
                user.getCharacterName());
    }

    private void revokeAllRefreshTokens(UUID userId, Instant now) {
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(token -> token.revoke(now));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.", "이메일 또는 비밀번호를 확인하세요.");
    }

    private void requireVerifiedEmail(UserEntity user) {
        if (emailVerificationRequired && !user.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "이메일 인증이 필요합니다.",
                    "가입한 이메일의 인증 메일을 확인한 뒤 다시 로그인하세요.");
        }
    }

    private void issueEmailVerification(UserEntity user) {
        requestEmailVerification(new EmailVerificationRequest(user.getEmail()));
    }
}
