package com.neulbom.backend.recording;

import java.util.UUID;

import com.neulbom.backend.analysis.integration.aiserver.AiAudioUrlSigner;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/ai-audio")
public class AiAudioDownloadController {

    private final RecordingRepository recordingRepository;
    private final RecordingStorage recordingStorage;
    private final AiAudioUrlSigner signer;

    public AiAudioDownloadController(
            RecordingRepository recordingRepository,
            RecordingStorage recordingStorage,
            AiAudioUrlSigner signer
    ) {
        this.recordingRepository = recordingRepository;
        this.recordingStorage = recordingStorage;
        this.signer = signer;
    }

    @GetMapping("/{recordingId}")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID recordingId,
            @RequestParam(name = "expires_at") long expiresAt,
            @RequestParam String signature
    ) {
        signer.verify(recordingId, expiresAt, signature);
        RecordingEntity recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException("녹음 파일을 찾을 수 없습니다."));
        RecordingStorage.StoredAudio audio = recordingStorage.load(recording.getStorageKey());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(mediaType(recording.getMimeType()))
                .contentLength(audio.content().length)
                .body(audio.content());
    }

    private MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
