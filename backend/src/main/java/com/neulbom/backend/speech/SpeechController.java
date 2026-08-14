package com.neulbom.backend.speech;

import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.speech.api.SpeechSynthesizeRequest;
import com.neulbom.backend.speech.api.SpeechSynthesizeResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/speech")
public class SpeechController {

    private final AuthService authService;
    private final SpeechService speechService;

    public SpeechController(AuthService authService, SpeechService speechService) {
        this.authService = authService;
        this.speechService = speechService;
    }

    @PostMapping("/synthesize")
    public SpeechSynthesizeResponse synthesize(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SpeechSynthesizeRequest request
    ) {
        UUID userId = authService.authenticatedUserId(jwt.getSubject());
        return speechService.synthesize(userId, request);
    }
}
