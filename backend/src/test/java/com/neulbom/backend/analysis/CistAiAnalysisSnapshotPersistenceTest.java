package com.neulbom.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CistAiAnalysisSnapshotPersistenceTest {

    @Autowired
    private CistAiAnalysisRepository analysisRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void savesAndReloadsCompletedFeatureSnapshot() throws Exception {
        UUID analysisId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-29T10:00:00Z");
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                "snapshot-" + UUID.randomUUID() + "@example.com",
                null,
                "스냅샷 저장 테스트",
                "elder",
                LocalDate.of(1945, 1, 1),
                "80s_plus",
                "female",
                null,
                true,
                now,
                now));
        UUID sessionId = UUID.randomUUID();
        sessionRepository.saveAndFlush(new SessionEntity(
                sessionId, user.getId(), "cist", 17, "{}", false, now));
        String featureSnapshot = """
                {"schema_version":"cognitive-feature-snapshot-v1", "model_score":0.61}
                """.trim();
        CistAiAnalysisEntity analysis = new CistAiAnalysisEntity(
                analysisId,
                sessionId,
                "pending",
                "snapshot-test-create-key",
                "request-hash",
                "{}",
                now,
                now);
        analysis.updateStatus(
                "completed",
                false,
                null,
                null,
                "{}",
                new BigDecimal("0.61"),
                "model-v2",
                new BigDecimal("0.38592870327757767"),
                new BigDecimal("0.8061380697921943"),
                "fusion-threshold-v2",
                true,
                "monitoring_needed",
                now.plusSeconds(1));
        analysis.updateFeatureSnapshot(featureSnapshot);

        analysisRepository.saveAndFlush(analysis);
        entityManager.clear();

        CistAiAnalysisEntity reloaded = analysisRepository.findById(analysisId).orElseThrow();
        assertThat(objectMapper.readTree(reloaded.getFeatureSnapshot()))
                .isEqualTo(objectMapper.readTree(featureSnapshot));
    }
}
