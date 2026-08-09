package com.neulbom.backend.recording;

import java.time.Instant;
import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.recording.api.RecordingStatusResponse;
import com.neulbom.backend.recording.api.RecordingUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/recordings")
public class RecordingController {

    private final AuthService authService;
    private final RecordingService recordingService;

    public RecordingController(AuthService authService, RecordingService recordingService) {
        this.authService = authService;
        this.recordingService = recordingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecordingUploadResponse> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart(name = "audio_file") MultipartFile audioFile,
            @RequestParam(name = "client_recording_id") UUID clientRecordingId,
            @RequestParam(name = "user_id") UUID userId,
            @RequestParam String purpose,
            @RequestParam(name = "session_id", required = false) UUID sessionId,
            @RequestParam(name = "question_id", required = false) UUID questionId,
            @RequestParam(name = "recorded_at") Instant recordedAt,
            @RequestParam(name = "device_status", required = false) String deviceStatus
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recordingService.upload(
                        authenticatedUserId(jwt),
                        audioFile,
                        clientRecordingId,
                        userId,
                        purpose,
                        sessionId,
                        questionId,
                        recordedAt,
                        deviceStatus));
    }

    @GetMapping("/{recordingId}")
    public RecordingStatusResponse getStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID recordingId
    ) {
        return recordingService.getStatus(authenticatedUserId(jwt), recordingId);
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
