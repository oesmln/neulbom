package com.neulbom.backend.analysis;

import java.util.UUID;

import com.neulbom.backend.analysis.api.CistAiAnalysisResponse;
import com.neulbom.backend.analysis.integration.aiserver.AiServerContracts.RecognitionPlanResponse;
import com.neulbom.backend.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/cist-ai")
public class CistAiAnalysisController {

    private final CistAiAnalysisService service;
    private final AuthService authService;

    public CistAiAnalysisController(CistAiAnalysisService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping("/recognition-plan")
    public RecognitionPlanResponse createRecognitionPlan(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.createRecognitionPlan(authenticatedUserId(jwt), sessionId);
    }

    @PostMapping("/analyses")
    public ResponseEntity<CistAiAnalysisResponse> createAnalysis(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.createAnalysis(authenticatedUserId(jwt), sessionId));
    }

    @GetMapping("/analyses")
    public CistAiAnalysisResponse refreshAnalysis(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.refreshAnalysis(authenticatedUserId(jwt), sessionId);
    }

    @PostMapping("/analyses/retry")
    public ResponseEntity<CistAiAnalysisResponse> retryAnalysis(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.retryAnalysis(authenticatedUserId(jwt), sessionId));
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
