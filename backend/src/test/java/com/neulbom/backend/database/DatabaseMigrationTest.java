package com.neulbom.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

    private static final String MIGRATION_EMAIL = "migration-test@example.com";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsCreateCoreTablesAndReferenceSeeds() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE",
                Integer.class);
        Integer voiceProfileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM voice_profiles",
                Integer.class);
        Integer questionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions",
                Integer.class);
        Integer coreTableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'PUBLIC'
                          AND table_name IN (
                              'USERS', 'VOICE_PROFILES', 'USER_PROFILES', 'USER_PREFERENCES', 'REFRESH_TOKENS', 'CONSENTS',
                              'GUARDIAN_LINKS', 'GUARDIAN_LINK_SCOPES', 'GUARDIAN_INVITATIONS',
                              'GUARDIAN_INVITATION_SCOPES', 'QUESTIONS', 'SESSIONS', 'RECORDINGS',
                              'TRANSCRIPTS', 'ANSWERS', 'ACOUSTIC_ANALYSES', 'COGNITIVE_ANALYSES',
                              'SCREENING_RESULTS', 'SESSION_SUMMARIES', 'DIARIES', 'DIARY_REACTIONS',
                              'GAME_RESULTS', 'CHARACTERS', 'XP_LEDGER', 'CAMPAIGNS',
                              'CAMPAIGN_PARTICIPATIONS', 'NOTIFICATIONS', 'AUDIT_LOGS',
                              'PASSWORD_RESET_TOKENS', 'OAUTH_ACCOUNTS', 'DAILY_SUMMARIES',
                              'DIARY_GENERATION_JOBS', 'REPORT_EXPORTS', 'COUNSELING_CENTERS',
                              'CIST_RECOGNITION_PLANS', 'CIST_AI_ANALYSES', 'AI_SERVER_OPERATIONS'
                          )
                        """,
                Integer.class);

        assertThat(migrationCount).isGreaterThanOrEqualTo(10);
        assertThat(voiceProfileCount).isEqualTo(2);
        assertThat(questionCount).isEqualTo(22);
        assertThat(coreTableCount).isEqualTo(37);
    }

    @Test
    void usersRejectDuplicateEmailAtDatabaseLevel() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        insertUser(firstUserId, MIGRATION_EMAIL);

        assertThatThrownBy(() -> insertUser(secondUserId, MIGRATION_EMAIL))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guardianScopesRejectDuplicateScopeForSameLink() {
        UUID guardianId = UUID.randomUUID();
        UUID elderId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        insertUser(guardianId, "guardian-" + guardianId + "@example.com", "guardian");
        insertUser(elderId, "elder-" + elderId + "@example.com", "elder");

        jdbcTemplate.update(
                "INSERT INTO guardian_links (id, guardian_id, elder_id, status, consent_required) VALUES (?, ?, ?, 'active', FALSE)",
                linkId, guardianId, elderId);
        jdbcTemplate.update(
                "INSERT INTO guardian_link_scopes (link_id, scope) VALUES (?, 'screening')",
                linkId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO guardian_link_scopes (link_id, scope) VALUES (?, 'screening')",
                linkId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guardianLinksRejectDuplicateGuardianAndElderPair() {
        UUID guardianId = UUID.randomUUID();
        UUID elderId = UUID.randomUUID();
        insertUser(guardianId, "link-guardian-" + guardianId + "@example.com", "guardian");
        insertUser(elderId, "link-elder-" + elderId + "@example.com", "elder");
        jdbcTemplate.update(
                "INSERT INTO guardian_links (id, guardian_id, elder_id, status, consent_required) VALUES (?, ?, ?, 'pending', TRUE)",
                UUID.randomUUID(), guardianId, elderId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO guardian_links (id, guardian_id, elder_id, status, consent_required) VALUES (?, ?, ?, 'active', FALSE)",
                UUID.randomUUID(), guardianId, elderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recordingsRejectDuplicateOfflineClientId() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID firstRecordingId = UUID.randomUUID();
        UUID secondRecordingId = UUID.randomUUID();
        UUID clientRecordingId = UUID.randomUUID();
        insertUser(userId, "recording-" + userId + "@example.com");
        insertSession(sessionId, userId);

        insertRecording(firstRecordingId, clientRecordingId, userId, sessionId);

        assertThatThrownBy(() -> insertRecording(secondRecordingId, clientRecordingId, userId, sessionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void diaryRecordingAllowsNoSessionOrQuestionButAnswerRecordingRequiresBoth() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "diary-recording-" + userId + "@example.com");

        int inserted = jdbcTemplate.update(
                """
                        INSERT INTO recordings (
                            id, client_recording_id, user_id, purpose, session_id, question_id,
                            storage_key, mime_type, file_size_bytes, recorded_at
                        ) VALUES (?, ?, ?, 'diary', NULL, NULL, 'migration/diary.wav', 'audio/wav', 1, CURRENT_TIMESTAMP)
                        """,
                UUID.randomUUID(), UUID.randomUUID(), userId);

        assertThat(inserted).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO recordings (
                            id, client_recording_id, user_id, purpose, session_id, question_id,
                            storage_key, mime_type, file_size_bytes, recorded_at
                        ) VALUES (?, ?, ?, 'answer', NULL, NULL, 'migration/answer.wav', 'audio/wav', 1, CURRENT_TIMESTAMP)
                        """,
                UUID.randomUUID(), UUID.randomUUID(), userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void gameResultsRejectDuplicateClientResultId() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID clientResultId = UUID.randomUUID();
        insertUser(userId, "game-result-" + userId + "@example.com");
        insertSession(sessionId, userId);

        insertGameResult(UUID.randomUUID(), clientResultId, userId, sessionId);

        assertThatThrownBy(() -> insertGameResult(UUID.randomUUID(), clientResultId, userId, sessionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dailySummariesRejectDuplicateUserAndLocalDate() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "daily-summary-" + userId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO daily_summaries (id, user_id, local_date) VALUES (?, ?, DATE '2026-08-08')",
                UUID.randomUUID(), userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO daily_summaries (id, user_id, local_date) VALUES (?, ?, DATE '2026-08-08')",
                UUID.randomUUID(), userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notificationsRejectDuplicateEventKeyForSameRecipient() {
        UUID userId = UUID.randomUUID();
        UUID firstNotificationId = UUID.randomUUID();
        insertUser(userId, "notification-event-" + userId + "@example.com");
        jdbcTemplate.update(
                """
                        INSERT INTO notifications (
                            id, recipient_user_id, title, body, type, severity, event_key
                        ) VALUES (?, ?, '알림', '내용', 'reminder', 'info', ?)
                        """,
                firstNotificationId, userId, "event-" + userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO notifications (
                            id, recipient_user_id, title, body, type, severity, event_key
                        ) VALUES (?, ?, '알림 중복', '내용', 'reminder', 'info', ?)
                        """,
                UUID.randomUUID(), userId, "event-" + userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reportExportsRejectInvalidDateRange() {
        UUID guardianId = UUID.randomUUID();
        UUID elderId = UUID.randomUUID();
        insertUser(guardianId, "report-guardian-" + guardianId + "@example.com", "guardian");
        insertUser(elderId, "report-elder-" + elderId + "@example.com", "elder");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO report_exports (
                            id, guardian_id, elder_id, request_key, from_date, to_date, format
                        ) VALUES (?, ?, ?, ?, DATE '2026-08-09', DATE '2026-08-08', 'pdf')
                        """,
                UUID.randomUUID(), guardianId, elderId, UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void answersRejectDuplicateClientIdWithinSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID clientAnswerId = UUID.randomUUID();
        insertUser(userId, "answer-" + userId + "@example.com");
        insertSession(sessionId, userId);

        insertAnswer(UUID.randomUUID(), sessionId, clientAnswerId);

        assertThatThrownBy(() -> insertAnswer(UUID.randomUUID(), sessionId, clientAnswerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void campaignParticipationRejectsDuplicateUserAndCampaignPair() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        insertUser(userId, "campaign-" + userId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO campaigns (id, title, start_date, end_date) VALUES (?, 'migration campaign', CURRENT_DATE, CURRENT_DATE)",
                campaignId);
        jdbcTemplate.update(
                "INSERT INTO campaign_participations (id, campaign_id, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), campaignId, userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO campaign_participations (id, campaign_id, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), campaignId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(UUID userId, String email) {
        insertUser(userId, email, "elder");
    }

    private void insertUser(UUID userId, String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, name, role) VALUES (?, ?, 'migration test', ?)",
                userId, email, role);
    }

    private void insertSession(UUID sessionId, UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO sessions (id, user_id, total_questions) VALUES (?, ?, 5)",
                sessionId, userId);
    }

    private void insertRecording(UUID recordingId, UUID clientRecordingId, UUID userId, UUID sessionId) {
        jdbcTemplate.update(
                """
                        INSERT INTO recordings (
                            id, client_recording_id, user_id, session_id, question_id,
                            storage_key, mime_type, file_size_bytes, recorded_at
                        ) VALUES (?, ?, ?, ?, '00000000-0000-0000-0000-000000000101', 'migration/test.wav', 'audio/wav', 1, CURRENT_TIMESTAMP)
                        """,
                recordingId, clientRecordingId, userId, sessionId);
    }

    private void insertAnswer(UUID answerId, UUID sessionId, UUID clientAnswerId) {
        jdbcTemplate.update(
                """
                        INSERT INTO answers (
                            id, session_id, question_id, client_answer_id, answer_text, answered_at
                        ) VALUES (?, ?, '00000000-0000-0000-0000-000000000101', ?, 'migration answer', CURRENT_TIMESTAMP)
                        """,
                answerId, sessionId, clientAnswerId);
    }

    private void insertGameResult(
            UUID resultId,
            UUID clientResultId,
            UUID userId,
            UUID sessionId
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO game_results (
                            id, client_game_result_id, user_id, session_id, game_type, score,
                            response_times, error_count, total_questions, matched_pairs, attempt_count
                        ) VALUES (?, ?, ?, ?, 'image_match', 100, '[]', 0, 6, 6, 6)
                        """,
                resultId, clientResultId, userId, sessionId);
    }
}
