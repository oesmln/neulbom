package com.neulbom.backend.analysis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import org.junit.jupiter.api.Test;

class DailyCognitiveAnalysisStatusSynchronizerTest {

    @Test
    void synchronizesEveryInFlightDailyAnalysisAndContinuesAfterFailure() {
        CistAiAnalysisRepository analysisRepository = mock(CistAiAnalysisRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        CistAiAnalysisService analysisService = mock(CistAiAnalysisService.class);
        CistAiAnalysisEntity first = mock(CistAiAnalysisEntity.class);
        CistAiAnalysisEntity second = mock(CistAiAnalysisEntity.class);
        SessionEntity firstSession = mock(SessionEntity.class);
        SessionEntity secondSession = mock(SessionEntity.class);
        UUID firstSessionId = UUID.randomUUID();
        UUID secondSessionId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        when(first.getSessionId()).thenReturn(firstSessionId);
        when(second.getSessionId()).thenReturn(secondSessionId);
        when(sessionRepository.findById(firstSessionId)).thenReturn(java.util.Optional.of(firstSession));
        when(sessionRepository.findById(secondSessionId)).thenReturn(java.util.Optional.of(secondSession));
        when(firstSession.getId()).thenReturn(firstSessionId);
        when(secondSession.getId()).thenReturn(secondSessionId);
        when(firstSession.getUserId()).thenReturn(firstUserId);
        when(secondSession.getUserId()).thenReturn(secondUserId);
        when(analysisRepository
                .findTop100ByBaselineAnalysisIdIsNotNullAndStatusInOrderByUpdatedAtAsc(
                        Set.of("pending", "processing")))
                .thenReturn(List.of(first, second));
        when(analysisService.refreshDailyAnalysis(firstUserId, firstSessionId))
                .thenThrow(new IllegalStateException("provider unavailable"));

        new DailyCognitiveAnalysisStatusSynchronizer(
                analysisRepository, sessionRepository, analysisService)
                .synchronizeInFlightAnalyses();

        verify(analysisService).refreshDailyAnalysis(firstUserId, firstSessionId);
        verify(analysisService).refreshDailyAnalysis(secondUserId, secondSessionId);
    }
}
