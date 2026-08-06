package com.neulbom.backend.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.neulbom.backend.auth.api.AuthTokenResponse;
import com.neulbom.backend.auth.api.LoginRequest;
import com.neulbom.backend.auth.api.OAuthLoginRequest;
import com.neulbom.backend.auth.api.PasswordResetConfirmRequest;
import com.neulbom.backend.auth.api.PasswordResetRequest;
import com.neulbom.backend.auth.api.PasswordResetRequestResponse;
import com.neulbom.backend.auth.api.RefreshRequest;
import com.neulbom.backend.auth.api.RegisterRequest;
import com.neulbom.backend.auth.api.RegisterResponse;
import com.neulbom.backend.auth.api.LogoutRequest;
import com.neulbom.backend.auth.oauth.OAuthProfile;
import com.neulbom.backend.auth.oauth.OAuthProviderClientRegistry;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.user.OAuthAccountEntity;
import com.neulbom.backend.user.OAuthAccountRepository;
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

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final UuidGenerator uuidGenerator;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final JwtTokenService jwtTokenService;
    private final PasswordResetNotifier passwordResetNotifier;
    private final OAuthProviderClientRegistry oauthProviderClientRegistry;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            OAuthAccountRepository oauthAccountRepository,
            PasswordEncoder passwordEncoder,
            UuidGenerator uuidGenerator,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            JwtTokenService jwtTokenService,
            PasswordResetNotifier passwordResetNotifier,
            OAuthProviderClientRegistry oauthProviderClientRegistry
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.uuidGenerator = uuidGenerator;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.jwtTokenService = jwtTokenService;
        this.passwordResetNotifier = passwordResetNotifier;
        this.oauthProviderClientRegistry = oauthProviderClientRegistry;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.", "다른 이메일을 사용하세요.");
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
        userRepository.save(user);
        return new RegisterResponse(user.getId(), user.getRole(), user.isProfileCompleted(), user.getCreatedAt());
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (!user.isActive() || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issueTokens(user, false);
    }

    @Transactional
    public AuthTokenResponse loginWithOAuth(String provider, OAuthLoginRequest request) {
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        OAuthProfile profile = oauthProviderClientRegistry.clientFor(normalizedProvider).fetchProfile(request);
        if (!normalizedProvider.equals(profile.provider())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다.", "소셜 provider 정보가 일치하지 않습니다.");
        }
        if (!profile.emailVerified() || profile.email() == null || profile.email().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "소셜 인증에 실패했습니다.", "검증된 이메일을 제공하는 계정만 사용할 수 있습니다.");
        }

        Instant now = jwtTokenService.now();
        OAuthAccountEntity oauthAccount = oauthAccountRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .orElse(null);
        boolean newUser = false;
        UserEntity user;
        if (oauthAccount != null) {
            user = userRepository.findById(oauthAccount.getUserId()).orElseThrow(this::invalidCredentials);
            if (!user.isActive()) {
                throw invalidCredentials();
            }
            oauthAccount.updateProfile(profile.email(), profile.displayName(), now);
        } else {
            user = userRepository.findByEmailIgnoreCase(normalizeEmail(profile.email())).orElse(null);
            if (user != null && !user.isActive()) {
                throw invalidCredentials();
            }
            if (user == null) {
                if (request.role() == null || request.role().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "가입 역할이 필요합니다.", "신규 소셜 계정은 role을 함께 보내야 합니다.");
                }
                user = new UserEntity(
                        uuidGenerator.generate(),
                        normalizeEmail(profile.email()),
                        null,
                        profile.displayName() == null || profile.displayName().isBlank()
                                ? "소셜 사용자" : profile.displayName(),
                        request.role(),
                        null,
                        null,
                        null,
                        null,
                        false,
                        now,
                        now);
                userRepository.save(user);
                newUser = true;
            }
            oauthAccount = new OAuthAccountEntity(
                    uuidGenerator.generate(),
                    user.getId(),
                    profile.provider(),
                    profile.providerUserId(),
                    normalizeEmail(profile.email()),
                    profile.displayName(),
                    now,
                    now);
        }
        oauthAccountRepository.save(oauthAccount);
        return issueTokens(user, newUser);
    }

    @Transactional
    public PasswordResetRequestResponse requestPasswordReset(PasswordResetRequest request) {
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
    public AuthTokenResponse refresh(RefreshRequest request) {
        Instant now = jwtTokenService.now();
        RefreshTokenEntity oldToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHasher.hash(request.refreshToken()))
                .filter(token -> token.isUsable(now))
                .orElseThrow(this::invalidCredentials);
        UserEntity user = userRepository.findById(oldToken.getUserId()).orElseThrow(this::invalidCredentials);
        if (!user.isActive()) {
            throw invalidCredentials();
        }
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
                newUser);
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
}
