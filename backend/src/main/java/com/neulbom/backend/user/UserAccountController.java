package com.neulbom.backend.user;

import com.neulbom.backend.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserAccountController {

    private final AuthService authService;

    public UserAccountController(AuthService authService) {
        this.authService = authService;
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Jwt jwt) {
        authService.withdraw(authService.authenticatedUserId(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
