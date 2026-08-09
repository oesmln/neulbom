package com.neulbom.backend.game;

import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.config.ServerWorkerOnly;
import com.neulbom.backend.game.api.CharacterResponse;
import com.neulbom.backend.game.api.GameHistoryResponse;
import com.neulbom.backend.game.api.GameResultRequest;
import com.neulbom.backend.game.api.GameResultResponse;
import com.neulbom.backend.game.api.XpAwardRequest;
import com.neulbom.backend.game.api.XpAwardResponse;
import com.neulbom.backend.game.api.XpHistoryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GameController {

    private final AuthService authService;
    private final GameService gameService;

    public GameController(AuthService authService, GameService gameService) {
        this.authService = authService;
        this.gameService = gameService;
    }

    @PostMapping("/game/result")
    public GameResultResponse saveResult(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GameResultRequest request
    ) {
        return gameService.saveResult(authenticatedUserId(jwt), request);
    }

    @GetMapping("/game/{userId}/history")
    public GameHistoryResponse history(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String gameType,
            @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        return gameService.history(authenticatedUserId(jwt), userId, gameType, limit);
    }

    @GetMapping("/character/{userId}")
    public CharacterResponse character(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        return gameService.character(authenticatedUserId(jwt), userId);
    }

    @GetMapping("/character/{userId}/xp-history")
    public XpHistoryResponse xpHistory(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor
    ) {
        return gameService.xpHistory(authenticatedUserId(jwt), userId, limit, cursor);
    }

    @ServerWorkerOnly
    @PostMapping("/character/{userId}/xp")
    public XpAwardResponse awardXp(
            @PathVariable UUID userId,
            @Valid @RequestBody XpAwardRequest request
    ) {
        return gameService.awardXp(userId, request);
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
