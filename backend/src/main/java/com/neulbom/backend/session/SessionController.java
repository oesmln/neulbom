package com.neulbom.backend.session;

import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.session.api.AnswerRequest;
import com.neulbom.backend.session.api.AnswerResponse;
import com.neulbom.backend.session.api.QuestionResponse;
import com.neulbom.backend.session.api.QuestionsResponse;
import com.neulbom.backend.session.api.SessionAnswersResponse;
import com.neulbom.backend.session.api.SessionEndResponse;
import com.neulbom.backend.session.api.SessionResponse;
import com.neulbom.backend.session.api.SessionSettingsUpdateRequest;
import com.neulbom.backend.session.api.SessionStartRequest;
import com.neulbom.backend.session.api.SessionsResponse;
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
@RequestMapping("/api/v1")
public class SessionController {

    private final AuthService authService;
    private final SessionService sessionService;

    public SessionController(AuthService authService, SessionService sessionService) {
        this.authService = authService;
        this.sessionService = sessionService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> startSession(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SessionStartRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.startSession(authenticatedUserId(jwt), request));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionResponse getSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return sessionService.getSession(authenticatedUserId(jwt), sessionId);
    }

    @PatchMapping("/sessions/{sessionId}/settings")
    public SessionResponse updateSettings(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SessionSettingsUpdateRequest request
    ) {
        return sessionService.updateSettings(authenticatedUserId(jwt), sessionId, request);
    }

    @PatchMapping("/sessions/{sessionId}/end")
    public SessionEndResponse endSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return sessionService.endSession(authenticatedUserId(jwt), sessionId);
    }

    @GetMapping("/sessions")
    public SessionsResponse listSessions(
            @RequestParam(name = "user_id") UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, name = "session_type") String sessionType,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return sessionService.listSessions(userId, authenticatedUserId(jwt), sessionType, date, page, limit);
    }

    @PostMapping("/sessions/{sessionId}/answers")
    public ResponseEntity<AnswerResponse> saveAnswer(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AnswerRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.saveAnswer(authenticatedUserId(jwt), sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/answers")
    public SessionAnswersResponse listAnswers(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return sessionService.listAnswers(authenticatedUserId(jwt), sessionId);
    }

    @GetMapping("/questions/daily")
    public QuestionsResponse listDailyQuestions(
            @RequestParam(name = "user_id") UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, name = "session_type") String sessionType,
            @RequestParam(required = false, name = "type") String questionType
    ) {
        return sessionService.listDailyQuestions(userId, authenticatedUserId(jwt), sessionType, questionType);
    }

    @GetMapping("/questions/{questionId}")
    public QuestionResponse getQuestion(@PathVariable UUID questionId) {
        return sessionService.getQuestion(questionId);
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
