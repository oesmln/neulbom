package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CistAnalysisStatusSynchronizerTest {

    private final CistAiAnalysisRepository analysisRepository = mock(CistAiAnalysisRepository.class);
    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final CistAiAnalysisService analysisService = mock(CistAiAnalysisService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
    private final CistAnalysisStatusSynchronizer synchronizer =
            new CistAnalysisStatusSynchronizer(analysisRepository, sessionRepository, analysisService, clock);

    @Test
    void retriesMissingAnalysisCreationAndContinuesAfterOneFailure(CapturedOutput output) {
        SessionEntity first = mock(SessionEntity.class);
        SessionEntity second = mock(SessionEntity.class);
        UUID firstSessionId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        when(first.getId()).thenReturn(firstSessionId);
        when(first.getUserId()).thenReturn(firstUserId);
        when(second.getId()).thenReturn(secondSessionId);
        when(second.getUserId()).thenReturn(secondUserId);
        when(sessionRepository.findEndedCistSessionsMissingAnalysis(
                clock.instant().minusSeconds(30), PageRequest.of(0, 100)))
                .thenReturn(List.of(first, second));
        when(analysisService.createAnalysis(firstUserId, firstSessionId))
                .thenThrow(new IllegalStateException("sensitive provider detail"));

        synchronizer.createMissingAnalyses();

        verify(analysisService).createAnalysis(firstUserId, firstSessionId);
        verify(analysisService).createAnalysis(secondUserId, secondSessionId);
        assertThat(output).contains("session_id=" + firstSessionId, "reason=IllegalStateException");
        assertThat(output).doesNotContain("sensitive provider detail");
    }

    @Test
    void synchronizesEveryInFlightFullCistAnalysisAndContinuesAfterFailure(CapturedOutput output) {
        CistAiAnalysisEntity first = mock(CistAiAnalysisEntity.class);
        CistAiAnalysisEntity second = mock(CistAiAnalysisEntity.class);
        SessionEntity firstSession = mock(SessionEntity.class);
        SessionEntity secondSession = mock(SessionEntity.class);
        UUID firstSessionId = UUID.randomUUID();
        UUID firstAnalysisId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        when(first.getSessionId()).thenReturn(firstSessionId);
        when(first.getAnalysisId()).thenReturn(firstAnalysisId);
        when(second.getSessionId()).thenReturn(secondSessionId);
        when(sessionRepository.findById(firstSessionId)).thenReturn(Optional.of(firstSession));
        when(sessionRepository.findById(secondSessionId)).thenReturn(Optional.of(secondSession));
        when(firstSession.getId()).thenReturn(firstSessionId);
        when(secondSession.getId()).thenReturn(secondSessionId);
        when(firstSession.getUserId()).thenReturn(firstUserId);
        when(secondSession.getUserId()).thenReturn(secondUserId);
        when(analysisRepository
                .findTop100ByBaselineAnalysisIdIsNullAndStatusInOrderByUpdatedAtAsc(
                        Set.of("pending", "processing")))
                .thenReturn(List.of(first, second));
        when(analysisService.refreshAnalysis(firstUserId, firstSessionId))
                .thenThrow(new IllegalStateException("provider unavailable"));

        synchronizer.synchronizeInFlightAnalyses();

        verify(analysisService).refreshAnalysis(firstUserId, firstSessionId);
        verify(analysisService).refreshAnalysis(secondUserId, secondSessionId);
        assertThat(output).contains("analysis_id=" + firstAnalysisId, "session_id=" + firstSessionId,
                "reason=IllegalStateException");
        assertThat(output).doesNotContain("provider unavailable");
    }

    @Test
    void skipsAnalysesWhoseSessionNoLongerExists() {
        CistAiAnalysisEntity orphan = mock(CistAiAnalysisEntity.class);
        UUID sessionId = UUID.randomUUID();
        when(orphan.getSessionId()).thenReturn(sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(analysisRepository
                .findTop100ByBaselineAnalysisIdIsNullAndStatusInOrderByUpdatedAtAsc(
                        Set.of("pending", "processing")))
                .thenReturn(List.of(orphan));

        synchronizer.synchronizeInFlightAnalyses();

        verify(analysisService, never()).refreshAnalysis(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void continuesAfterSessionLookupFails() {
        CistAiAnalysisEntity first = mock(CistAiAnalysisEntity.class);
        CistAiAnalysisEntity second = mock(CistAiAnalysisEntity.class);
        SessionEntity secondSession = mock(SessionEntity.class);
        UUID firstSessionId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        when(first.getSessionId()).thenReturn(firstSessionId);
        when(second.getSessionId()).thenReturn(secondSessionId);
        when(sessionRepository.findById(firstSessionId)).thenThrow(new IllegalStateException("database unavailable"));
        when(sessionRepository.findById(secondSessionId)).thenReturn(Optional.of(secondSession));
        when(secondSession.getId()).thenReturn(secondSessionId);
        when(secondSession.getUserId()).thenReturn(secondUserId);
        when(analysisRepository
                .findTop100ByBaselineAnalysisIdIsNullAndStatusInOrderByUpdatedAtAsc(
                        Set.of("pending", "processing")))
                .thenReturn(List.of(first, second));

        synchronizer.synchronizeInFlightAnalyses();

        verify(analysisService).refreshAnalysis(secondUserId, secondSessionId);
    }
}
