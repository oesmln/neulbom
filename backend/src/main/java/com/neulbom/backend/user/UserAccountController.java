package com.neulbom.backend.user;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.auth.api.PasswordChangeRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
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

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        authService.changePassword(authService.authenticatedUserId(jwt.getSubject()), request);
        return ResponseEntity.noContent().build();
    }
}
