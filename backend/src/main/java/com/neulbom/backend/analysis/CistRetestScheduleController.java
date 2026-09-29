package com.neulbom.backend.analysis;

import java.util.UUID;

import com.neulbom.backend.analysis.api.CistRetestScheduleResponse;
import com.neulbom.backend.auth.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cist")
public class CistRetestScheduleController {
    private final CistRetestScheduleService service;
    private final AuthService authService;

    public CistRetestScheduleController(CistRetestScheduleService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/retest-schedule")
    public CistRetestScheduleResponse getSchedule(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = authService.authenticatedUserId(jwt.getSubject());
        return service.getSchedule(userId);
    }
}
