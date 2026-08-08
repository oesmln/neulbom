package com.neulbom.backend.diary;

import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.diary.api.CalendarActivitiesResponse;
import com.neulbom.backend.diary.api.DiaryCreateRequest;
import com.neulbom.backend.diary.api.DiaryFromDailySummaryRequest;
import com.neulbom.backend.diary.api.DiaryFromSessionRequest;
import com.neulbom.backend.diary.api.DiaryResponse;
import com.neulbom.backend.diary.api.DiaryUpdateRequest;
import com.neulbom.backend.diary.api.GenerationStatusResponse;
import com.neulbom.backend.diary.api.ReactionCreateRequest;
import com.neulbom.backend.diary.api.ReactionResponse;
import com.neulbom.backend.diary.api.ReactionsResponse;
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
@RequestMapping("/api/v1")
public class DiaryController {

    private final AuthService authService;
    private final DiaryService diaryService;

    public DiaryController(AuthService authService, DiaryService diaryService) {
        this.authService = authService;
        this.diaryService = diaryService;
    }

    @PostMapping("/diaries")
    public ResponseEntity<DiaryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DiaryCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.create(authenticatedUserId(jwt), request));
    }

    @PostMapping("/diaries/from-session")
    public ResponseEntity<DiaryResponse> createFromSession(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DiaryFromSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.createFromSession(authenticatedUserId(jwt), request));
    }

    @PostMapping("/diaries/from-daily-summary")
    public ResponseEntity<GenerationStatusResponse> createFromDailySummary(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DiaryFromDailySummaryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(diaryService.createFromDailySummary(authenticatedUserId(jwt), request));
    }

    @GetMapping("/diaries/{userId}/generation-status")
    public GenerationStatusResponse generationStatus(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam LocalDate date
    ) {
        return diaryService.generationStatus(authenticatedUserId(jwt), userId, date);
    }

    @GetMapping("/diaries/{id}")
    public Object getOrList(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        return diaryService.getByIdOrUser(authenticatedUserId(jwt), id, date, fromDate, toDate, page, limit);
    }

    @PatchMapping("/diaries/{diaryId}")
    public DiaryResponse update(
            @PathVariable UUID diaryId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DiaryUpdateRequest request
    ) {
        return diaryService.update(authenticatedUserId(jwt), diaryId, request);
    }

    @DeleteMapping("/diaries/{diaryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID diaryId, @AuthenticationPrincipal Jwt jwt) {
        diaryService.delete(authenticatedUserId(jwt), diaryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/diaries/{diaryId}/reactions")
    public ResponseEntity<ReactionResponse> createReaction(
            @PathVariable UUID diaryId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReactionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.createReaction(authenticatedUserId(jwt), diaryId, request));
    }

    @GetMapping("/diaries/{diaryId}/reactions")
    public ReactionsResponse reactions(@PathVariable UUID diaryId, @AuthenticationPrincipal Jwt jwt) {
        return diaryService.reactions(authenticatedUserId(jwt), diaryId);
    }

    @GetMapping("/calendar/{userId}/activities")
    public CalendarActivitiesResponse calendar(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "from_date") LocalDate fromDate,
            @RequestParam(name = "to_date") LocalDate toDate,
            @RequestParam(required = false) String types
    ) {
        return diaryService.calendar(authenticatedUserId(jwt), userId, fromDate, toDate, types);
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
