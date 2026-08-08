package com.neulbom.backend.guardian;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.guardian.api.EldersResponse;
import com.neulbom.backend.guardian.api.GuardianLinkRequest;
import com.neulbom.backend.guardian.api.GuardianLinkResponse;
import com.neulbom.backend.guardian.api.GuardianLinkUpdateRequest;
import com.neulbom.backend.guardian.api.InvitationAcceptRequest;
import com.neulbom.backend.guardian.api.InvitationCreateRequest;
import com.neulbom.backend.guardian.api.InvitationCreateResponse;
import com.neulbom.backend.guardian.api.InvitationVerifyRequest;
import com.neulbom.backend.guardian.api.InvitationVerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guardian")
public class GuardianController {

    private final AuthService authService;
    private final GuardianService guardianService;

    public GuardianController(AuthService authService, GuardianService guardianService) {
        this.authService = authService;
        this.guardianService = guardianService;
    }

    @PostMapping("/invitations")
    public ResponseEntity<InvitationCreateResponse> createInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InvitationCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guardianService.createInvitation(authenticatedUserId(jwt), request));
    }

    @PostMapping("/invitations/verify")
    public InvitationVerifyResponse verifyInvitation(
            @Valid @RequestBody InvitationVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        return guardianService.verifyInvitation(request.inviteCode(), httpRequest.getRemoteAddr());
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<GuardianLinkResponse> acceptInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InvitationAcceptRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guardianService.acceptInvitation(authenticatedUserId(jwt), request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/link")
    public ResponseEntity<GuardianLinkResponse> createLink(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GuardianLinkRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guardianService.createLink(authenticatedUserId(jwt), request));
    }

    @GetMapping("/{guardianId}/elders")
    public EldersResponse getElders(
            @PathVariable UUID guardianId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status
    ) {
        return guardianService.getElders(guardianId, authenticatedUserId(jwt), status);
    }

    @PatchMapping("/link/{linkId}")
    public GuardianLinkResponse updateLink(
            @PathVariable UUID linkId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GuardianLinkUpdateRequest request
    ) {
        return guardianService.updateLink(authenticatedUserId(jwt), linkId, request);
    }

    @DeleteMapping("/link/{linkId}")
    public ResponseEntity<Void> revokeLink(
            @PathVariable UUID linkId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        guardianService.revokeLink(authenticatedUserId(jwt), linkId);
        return ResponseEntity.noContent().build();
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
