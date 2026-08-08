package com.neulbom.backend.guardian;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.user.ConsentEntity;
import com.neulbom.backend.user.ConsentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GuardianAccessService {

    private final GuardianLinkRepository guardianLinkRepository;
    private final GuardianLinkScopeRepository guardianLinkScopeRepository;
    private final ConsentRepository consentRepository;

    public GuardianAccessService(
            GuardianLinkRepository guardianLinkRepository,
            GuardianLinkScopeRepository guardianLinkScopeRepository,
            ConsentRepository consentRepository
    ) {
        this.guardianLinkRepository = guardianLinkRepository;
        this.guardianLinkScopeRepository = guardianLinkScopeRepository;
        this.consentRepository = consentRepository;
    }

    public GuardianLinkEntity requireAccess(UUID guardianId, UUID elderId, String requiredScope) {
        GuardianLinkEntity link = guardianLinkRepository.findByGuardianIdAndElderId(guardianId, elderId)
                .filter(candidate -> GuardianLinkEntity.ACTIVE.equals(candidate.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "접근 권한이 없습니다.",
                        "활성 보호자 연결을 확인하세요."));

        if (link.isConsentRequired() && !hasAgreedGuardianConsent(elderId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "접근 권한이 없습니다.",
                    "보호자 접근 동의가 필요합니다.");
        }

        Set<String> scopes = Set.copyOf(scopes(link.getId()));
        if (!scopes.contains("all") && !scopes.contains(requiredScope)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "접근 권한이 없습니다.",
                    "연결된 접근 범위에 해당 기능이 없습니다.");
        }
        return link;
    }

    public boolean hasAgreedGuardianConsent(UUID elderId) {
        return consentRepository.findFirstByUserIdAndConsentTypeOrderByCreatedAtDesc(elderId, "guardian_access")
                .map(ConsentEntity::isAgreed)
                .orElse(false);
    }

    public List<String> scopes(UUID linkId) {
        return guardianLinkScopeRepository.findAllByIdLinkId(linkId)
                .stream()
                .map(scope -> scope.getId().getScope())
                .toList();
    }
}
