package com.neulbom.backend.notification;

import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.config.ServerWorkerOnly;
import com.neulbom.backend.notification.api.NotificationPushRequest;
import com.neulbom.backend.notification.api.NotificationPushResponse;
import com.neulbom.backend.notification.api.NotificationReadResponse;
import com.neulbom.backend.notification.api.NotificationsReadAllResponse;
import com.neulbom.backend.notification.api.NotificationsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final AuthService authService;
    private final NotificationService notificationService;

    public NotificationController(AuthService authService, NotificationService notificationService) {
        this.authService = authService;
        this.notificationService = notificationService;
    }

    @PostMapping("/push")
    @ServerWorkerOnly
    public ResponseEntity<NotificationPushResponse> push(
            @Valid @RequestBody NotificationPushRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.push(request));
    }

    @GetMapping("/{userId}")
    public NotificationsResponse list(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "unread_only", defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return notificationService.list(authenticatedUserId(jwt), userId, unreadOnly, type, limit);
    }

    @PatchMapping("/read-all")
    public NotificationsReadAllResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.markAllRead(authenticatedUserId(jwt));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationReadResponse markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return notificationService.markRead(authenticatedUserId(jwt), notificationId);
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
