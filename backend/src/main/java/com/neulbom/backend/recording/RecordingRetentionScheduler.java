package com.neulbom.backend.recording;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RecordingRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordingRetentionScheduler.class);

    private final RecordingRepository recordingRepository;
    private final RecordingService recordingService;
    private final Clock clock;
    private final int retentionDays;

    public RecordingRetentionScheduler(
            RecordingRepository recordingRepository,
            RecordingService recordingService,
            Clock clock,
            @Value("${app.storage.retention-days:30}") int retentionDays
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("녹음 원본 보존 기간은 1일 이상이어야 합니다.");
        }
        this.recordingRepository = recordingRepository;
        this.recordingService = recordingService;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 86_400_000)
    public void deleteExpiredRecordings() {
        Instant cutoff = clock.instant().minus(java.time.Duration.ofDays(retentionDays));
        while (true) {
            var expired = recordingRepository
                    .findTop100ByAudioDeletedAtIsNullAndRecordedAtBeforeOrderByRecordedAtAsc(cutoff);
            if (expired.isEmpty()) {
                return;
            }
            int deleted = 0;
            for (RecordingEntity recording : expired) {
                try {
                    if (recordingService.deleteExpiredAudio(recording.getId(), cutoff)) {
                        deleted++;
                    }
                } catch (RuntimeException exception) {
                    log.warn("만료된 녹음 원본 삭제 실패 recording_id={} reason={}",
                            recording.getId(), exception.getClass().getSimpleName());
                }
            }
            if (deleted == 0) {
                return;
            }
        }
    }
}
