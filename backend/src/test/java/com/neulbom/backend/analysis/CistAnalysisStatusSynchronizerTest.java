package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CistAnalysisStatusSynchronizerTest {

    private final CistAiAnalysisRepository analysisRepository = mock(CistAiAnalysisRepository.class);
    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final CistAiAnalysisService analysisService = mock(CistAiAnalysisService.class);
    private final CistAnalysisStatusSynchronizer synchronizer =
            new CistAnalysisStatusSynchronizer(analysisRepository, sessionRepository, analysisService);

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
