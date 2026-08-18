package com.neulbom.backend.guardian;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.auth.service.TokenHasher;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.guardian.api.ElderSummaryResponse;
import com.neulbom.backend.guardian.api.EldersResponse;
import com.neulbom.backend.guardian.api.GuardianLinkRequest;
import com.neulbom.backend.guardian.api.GuardianLinkResponse;
import com.neulbom.backend.guardian.api.GuardianLinkUpdateRequest;
import com.neulbom.backend.guardian.api.InvitationAcceptRequest;
import com.neulbom.backend.guardian.api.InvitationCreateRequest;
import com.neulbom.backend.guardian.api.InvitationCreateResponse;
import com.neulbom.backend.guardian.api.InvitationVerifyResponse;
import com.neulbom.backend.user.ConsentEntity;
import com.neulbom.backend.user.ConsentRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuardianService {

    public static final Set<String> ACCESS_SCOPES = Set.of(
            "screening", "summary", "diary", "activity", "campaign", "all");
    private static final List<String> DEFAULT_SCOPES = List.of("screening", "summary", "diary", "activity");
    private static final Duration DEFAULT_INVITATION_TTL = Duration.ofMinutes(10);
    private static final int MAX_INVITATION_ATTEMPTS = 5;
    private static final String GUARDIAN_CONSENT_TYPE = "guardian_access";

    private final UserRepository userRepository;
    private final GuardianInvitationRepository invitationRepository;
    private final GuardianInvitationScopeRepository invitationScopeRepository;
    private final GuardianLinkRepository linkRepository;
    private final GuardianLinkScopeRepository linkScopeRepository;
    private final ConsentRepository consentRepository;
    private final GuardianAccessService accessService;
    private final TokenHasher tokenHasher;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final InvitationAttemptLimiter invitationAttemptLimiter;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public GuardianService(
            UserRepository userRepository,
            GuardianInvitationRepository invitationRepository,
            GuardianInvitationScopeRepository invitationScopeRepository,
            GuardianLinkRepository linkRepository,
            GuardianLinkScopeRepository linkScopeRepository,
            ConsentRepository consentRepository,
            GuardianAccessService accessService,
            TokenHasher tokenHasher,
            InviteCodeGenerator inviteCodeGenerator,
            InvitationAttemptLimiter invitationAttemptLimiter,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.invitationScopeRepository = invitationScopeRepository;
        this.linkRepository = linkRepository;
        this.linkScopeRepository = linkScopeRepository;
        this.consentRepository = consentRepository;
        this.accessService = accessService;
        this.tokenHasher = tokenHasher;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.invitationAttemptLimiter = invitationAttemptLimiter;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public InvitationCreateResponse createInvitation(UUID authenticatedUserId, InvitationCreateRequest request) {
        UserEntity guardian = requireRole(authenticatedUserId, "guardian");
        List<String> scopes = normalizeScopes(request.accessScope());
        int ttlSeconds = request.expiresIn() == null
                ? (int) DEFAULT_INVITATION_TTL.toSeconds() : request.expiresIn();
        Instant now = clock.instant();
        String inviteCode;
        GuardianInvitationEntity invitation;
        do {
            inviteCode = inviteCodeGenerator.generate();
            invitation = new GuardianInvitationEntity(
                    uuidGenerator.generate(),
                    guardian.getId(),
                    tokenHasher.hash(inviteCode),
                    normalizeOptional(request.relation()),
                    now.plusSeconds(ttlSeconds),
                    MAX_INVITATION_ATTEMPTS,
                    now);
        } while (invitationRepository.findByCodeHash(invitation.getCodeHash()).isPresent());

        invitationRepository.save(invitation);
        UUID invitationId = invitation.getId();
        invitationScopeRepository.saveAll(scopes.stream()
                .map(scope -> new GuardianInvitationScopeEntity(invitationId, scope))
                .toList());
        return new InvitationCreateResponse(
                invitation.getId(),
                inviteCode,
                invitation.getStatus(),
                invitation.getRelation(),
                scopes,
                invitation.getExpiresAt());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public InvitationVerifyResponse verifyInvitation(String inviteCode, String clientIp) {
        invitationAttemptLimiter.check(clientIp);
        GuardianInvitationEntity invitation;
        try {
            invitation = resolveInvitation(inviteCode);
        } catch (ApiException exception) {
            if (exception.status() == HttpStatus.NOT_FOUND) {
                invitationAttemptLimiter.recordFailure(clientIp);
            }
            throw exception;
        }
        invitationAttemptLimiter.recordSuccess(clientIp);
        return new InvitationVerifyResponse(
                invitation.getId(),
                invitation.getStatus(),
                invitation.getRelation(),
                invitationScopes(invitation.getId()),
                invitation.getExpiresAt(),
                true);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public GuardianLinkResponse acceptInvitation(UUID authenticatedUserId, InvitationAcceptRequest request, String clientIp) {
        UserEntity elder = requireRole(authenticatedUserId, "elder");
        if (!Boolean.TRUE.equals(request.consentAgreed())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "보호자 접근 동의가 필요합니다.", "동의하지 않은 초대는 수락할 수 없습니다.");
        }
        invitationAttemptLimiter.check(clientIp);
        GuardianInvitationEntity invitation;
        try {
            invitation = resolveInvitation(request.inviteCode());
        } catch (ApiException exception) {
            if (exception.status() == HttpStatus.NOT_FOUND) {
                invitationAttemptLimiter.recordFailure(clientIp);
            }
            throw exception;
        }
        invitationAttemptLimiter.recordSuccess(clientIp);
        if (invitation.getGuardianId().equals(elder.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "자기 자신과 연결할 수 없습니다.", "보호자와 고령자 계정을 확인하세요.");
        }
        UserEntity guardian = userRepository.findById(invitation.getGuardianId())
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("초대 발급자 정보를 찾을 수 없습니다."));
        if (!"guardian".equals(guardian.getRole())) {
            throw new ApiException(HttpStatus.CONFLICT, "유효하지 않은 초대입니다.", "보호자 계정을 확인하세요.");
        }
        if (linkRepository.findByGuardianIdAndElderId(guardian.getId(), elder.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 연결된 보호자입니다.", "기존 연결을 확인하세요.");
        }

        Instant now = clock.instant();
        saveAgreedGuardianConsent(elder.getId(), now);
        GuardianLinkEntity link = new GuardianLinkEntity(
                uuidGenerator.generate(),
                guardian.getId(),
                elder.getId(),
                invitation.getRelation(),
                GuardianLinkEntity.ACTIVE,
                false,
                now,
                now);
        linkRepository.save(link);
        List<String> scopes = invitationScopes(invitation.getId());
        linkScopeRepository.saveAll(scopes.stream()
                .map(scope -> new GuardianLinkScopeEntity(link.getId(), scope))
                .toList());
        invitation.accept(elder.getId(), now);
        invitationRepository.save(invitation);
        return toLinkResponse(null, link, scopes);
    }

    @Transactional
    public GuardianLinkResponse createLink(UUID authenticatedUserId, GuardianLinkRequest request) {
        UserEntity guardian = requireRole(authenticatedUserId, "guardian");
        if (request.guardianId() != null && !request.guardianId().equals(guardian.getId())) {
            throw new AccessDeniedException("JWT 사용자와 guardian_id가 일치하지 않습니다.");
        }
        UserEntity elder = userRepository.findById(request.elderId())
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("연결할 고령자 정보를 찾을 수 없습니다."));
        if (!"elder".equals(elder.getRole())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "연결할 수 없는 사용자입니다.", "elder 역할만 연결할 수 있습니다.");
        }
        if (guardian.getId().equals(elder.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "자기 자신과 연결할 수 없습니다.", "보호자와 고령자 계정을 확인하세요.");
        }
        if (linkRepository.findByGuardianIdAndElderId(guardian.getId(), elder.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 연결된 보호자입니다.", "기존 연결을 확인하세요.");
        }

        Instant now = clock.instant();
        GuardianLinkEntity link = new GuardianLinkEntity(
                uuidGenerator.generate(),
                guardian.getId(),
                elder.getId(),
                normalizeOptional(request.relation()),
                GuardianLinkEntity.PENDING,
                true,
                now,
                now);
        linkRepository.save(link);
        List<String> scopes = normalizeScopes(request.accessScope());
        linkScopeRepository.saveAll(scopes.stream()
                .map(scope -> new GuardianLinkScopeEntity(link.getId(), scope))
                .toList());
        return toLinkResponse(null, link, scopes);
    }

    @Transactional(readOnly = true)
    public EldersResponse getElders(UUID requestedGuardianId, UUID authenticatedUserId, String status) {
        if (!requestedGuardianId.equals(authenticatedUserId)) {
            throw new AccessDeniedException("본인의 보호자 연결 목록만 조회할 수 있습니다.");
        }
        requireRole(authenticatedUserId, "guardian");
        if (status != null && !Set.of(GuardianLinkEntity.PENDING, GuardianLinkEntity.ACTIVE, GuardianLinkEntity.REVOKED).contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "status 허용값을 확인하세요.");
        }
        List<ElderSummaryResponse> elders = linkRepository.findAllByGuardianIdOrderByCreatedAtDesc(authenticatedUserId)
                .stream()
                .filter(link -> status == null || status.equals(link.getStatus()))
                .map(link -> toElderSummary(link))
                .toList();
        return new EldersResponse(elders);
    }

    @Transactional
    public GuardianLinkResponse updateLink(UUID authenticatedUserId, UUID linkId, GuardianLinkUpdateRequest request) {
        requireRole(authenticatedUserId, "guardian");
        GuardianLinkEntity link = ownedLink(authenticatedUserId, linkId);
        String status = request.status() == null ? link.getStatus() : request.status();
        if (!Set.of(GuardianLinkEntity.ACTIVE, GuardianLinkEntity.REVOKED).contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "status는 active 또는 revoked여야 합니다.");
        }
        if (GuardianLinkEntity.ACTIVE.equals(status)
                && link.isConsentRequired()
                && !accessService.hasAgreedGuardianConsent(link.getElderId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "보호자 접근 동의가 필요합니다.", "동의가 완료된 연결만 활성화할 수 있습니다.");
        }
        List<String> scopes = request.accessScope() == null
                ? accessService.scopes(link.getId()) : normalizeScopes(request.accessScope());
        Instant now = clock.instant();
        link.update(status, request.relation() == null ? link.getRelation() : normalizeOptional(request.relation()), now);
        linkRepository.save(link);
        replaceScopes(link.getId(), scopes);
        return toLinkResponse(null, link, scopes);
    }

    @Transactional
    public void revokeLink(UUID authenticatedUserId, UUID linkId) {
        requireRole(authenticatedUserId, "guardian");
        GuardianLinkEntity link = ownedLink(authenticatedUserId, linkId);
        link.update(GuardianLinkEntity.REVOKED, link.getRelation(), clock.instant());
        linkRepository.save(link);
    }

    private GuardianInvitationEntity resolveInvitation(String inviteCode) {
        String normalizedCode = inviteCode == null ? "" : inviteCode.trim();
        GuardianInvitationEntity invitation = invitationRepository.findByCodeHashForUpdate(tokenHasher.hash(normalizedCode))
                .orElseThrow(() -> new ResourceNotFoundException("초대 코드를 찾을 수 없습니다."));
        Instant now = clock.instant();
        if (GuardianInvitationEntity.REVOKED.equals(invitation.getStatus())
                && invitation.getAttemptCount() >= invitation.getMaxAttempts()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "초대 코드 검증 시도가 너무 많습니다.", "잠시 후 다시 시도하세요.");
        }
        if (!invitation.isUsable(now)) {
            invitationRepository.save(invitation);
            throw new ApiException(HttpStatus.GONE, "초대 코드가 만료되었거나 이미 사용되었습니다.", "새로운 초대 코드를 요청하세요.");
        }
        return invitation;
    }

    private UserEntity requireRole(UUID userId, String role) {
        UserEntity user = userRepository.findById(userId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
        if (!role.equals(user.getRole())) {
            throw new AccessDeniedException("요청 역할에 맞는 사용자만 사용할 수 있습니다.");
        }
        return user;
    }

    private GuardianLinkEntity ownedLink(UUID guardianId, UUID linkId) {
        GuardianLinkEntity link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("보호자 연결을 찾을 수 없습니다."));
        if (!guardianId.equals(link.getGuardianId())) {
            throw new AccessDeniedException("본인이 소유한 보호자 연결만 수정할 수 있습니다.");
        }
        return link;
    }

    private List<String> normalizeScopes(List<String> requestedScopes) {
        List<String> source = requestedScopes == null || requestedScopes.isEmpty()
                ? DEFAULT_SCOPES : requestedScopes;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String scope : source) {
            if (scope == null || !ACCESS_SCOPES.contains(scope.trim())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "access_scope 허용값을 확인하세요.");
            }
            normalized.add(scope.trim());
        }
        if (normalized.contains("all")) {
            return List.of("all");
        }
        return new ArrayList<>(normalized);
    }

    private List<String> invitationScopes(UUID invitationId) {
        return invitationScopeRepository.findAllByIdInvitationId(invitationId)
                .stream()
                .map(scope -> scope.getId().getScope())
                .toList();
    }

    private void replaceScopes(UUID linkId, List<String> scopes) {
        linkScopeRepository.deleteAll(linkScopeRepository.findAllByIdLinkId(linkId));
        linkScopeRepository.saveAll(scopes.stream()
                .map(scope -> new GuardianLinkScopeEntity(linkId, scope))
                .toList());
    }

    private void saveAgreedGuardianConsent(UUID elderId, Instant now) {
        if (accessService.hasAgreedGuardianConsent(elderId)) {
            return;
        }
        String version = "guardian-link-" + now.toEpochMilli();
        consentRepository.save(new ConsentEntity(
                uuidGenerator.generate(),
                elderId,
                GUARDIAN_CONSENT_TYPE,
                true,
                now,
                version,
                now));
    }

    private GuardianLinkResponse toLinkResponse(UUID invitationId, GuardianLinkEntity link, List<String> scopes) {
        return new GuardianLinkResponse(
                invitationId,
                link.getId(),
                link.getElderId(),
                link.getGuardianId(),
                link.getStatus(),
                scopes,
                link.isConsentRequired(),
                link.getCreatedAt(),
                link.getUpdatedAt());
    }

    private ElderSummaryResponse toElderSummary(GuardianLinkEntity link) {
        UserEntity elder = userRepository.findById(link.getElderId()).orElse(null);
        String consentStatus = !link.isConsentRequired()
                ? "agreed"
                : accessService.hasAgreedGuardianConsent(link.getElderId()) ? "agreed" : "required";
        return new ElderSummaryResponse(
                link.getElderId(),
                elder == null ? null : elder.getName(),
                link.getId(),
                link.getStatus(),
                accessService.scopes(link.getId()),
                consentStatus,
                null,
                null,
                null,
                null,
                null);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
