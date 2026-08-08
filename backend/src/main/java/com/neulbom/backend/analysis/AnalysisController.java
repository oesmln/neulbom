package com.neulbom.backend.analysis;

import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.analysis.api.AcousticAnalysisRequest;
import com.neulbom.backend.analysis.api.AcousticAnalysisResponse;
import com.neulbom.backend.analysis.api.CognitiveAnalysisRequest;
import com.neulbom.backend.analysis.api.CognitiveAnalysisResponse;
import com.neulbom.backend.analysis.api.DailySummariesResponse;
import com.neulbom.backend.analysis.api.DailySummaryRequest;
import com.neulbom.backend.analysis.api.DailySummaryResponse;
import com.neulbom.backend.analysis.api.SessionSummaryRequest;
import com.neulbom.backend.analysis.api.SessionSummaryResponse;
import com.neulbom.backend.analysis.api.TranscribeResponse;
import com.neulbom.backend.config.ServerWorkerOnly;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AuthService authService;

    public AnalysisController(AnalysisService analysisService, AuthService authService) {
        this.analysisService = analysisService;
        this.authService = authService;
    }

    @ServerWorkerOnly
    @PostMapping(value = "/voice/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscribeResponse transcribe(
            @RequestParam(name = "recording_id") UUID recordingId,
            @RequestParam(name = "user_id") UUID userId,
            @RequestParam(name = "session_id") UUID sessionId,
            @RequestParam(name = "question_id") UUID questionId,
            @RequestPart(name = "audio_file", required = false) MultipartFile audioFile
    ) {
        return analysisService.transcribe(recordingId, userId, sessionId, questionId, audioFile);
    }

    @ServerWorkerOnly
    @PostMapping("/analysis/acoustic")
    public AcousticAnalysisResponse analyzeAcoustic(@Valid @RequestBody AcousticAnalysisRequest request) {
        return analysisService.analyzeAcoustic(request);
    }

    @ServerWorkerOnly
    @PostMapping("/analysis/cognitive")
    public CognitiveAnalysisResponse analyzeCognitive(@Valid @RequestBody CognitiveAnalysisRequest request) {
        return analysisService.analyzeCognitive(request);
    }

    @ServerWorkerOnly
    @PostMapping("/summary/session")
    public SessionSummaryResponse createSessionSummary(@Valid @RequestBody SessionSummaryRequest request) {
        return analysisService.createSessionSummary(request);
    }

    @GetMapping("/summary/session/{sessionId}")
    public SessionSummaryResponse getSessionSummary(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return analysisService.getSessionSummary(authenticatedUserId(jwt), sessionId);
    }

    @ServerWorkerOnly
    @PostMapping("/summary/daily")
    public ResponseEntity<DailySummaryResponse> createDailySummary(@Valid @RequestBody DailySummaryRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(analysisService.createDailySummary(request));
    }

    @GetMapping("/summary/daily/{userId}")
    public DailySummariesResponse getDailySummaries(
            @PathVariable UUID userId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return analysisService.getDailySummaries(
                authenticatedUserId(jwt), userId, date, fromDate, toDate, page, limit);
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
