package com.neulbom.backend.recording;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecordingIntegrationTest {

    private static final UUID QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UuidGenerator uuidGenerator;

    @Test
    void answerRecordingUploadsDeduplicatesAndExposesProcessingStatus() throws Exception {
        UserEntity elder = saveUser("recording-owner");
        UUID sessionId = saveSession(elder.getId());
        UUID clientRecordingId = UUID.randomUUID();
        MockMultipartFile audio = wavFile("answer.wav");
        String recordedAt = Instant.now().minusSeconds(1).toString();

        String body = mockMvc.perform(multipart("/api/v1/recordings")
                        .file(audio)
                        .with(jwtFor(elder))
                        .param("client_recording_id", clientRecordingId.toString())
                        .param("user_id", elder.getId().toString())
                        .param("purpose", "answer")
                        .param("session_id", sessionId.toString())
                        .param("question_id", QUESTION_ID.toString())
                        .param("recorded_at", recordedAt)
                        .param("device_status", "server_pending"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recording_id").isNotEmpty())
                .andExpect(jsonPath("$.client_recording_id").value(clientRecordingId.toString()))
                .andExpect(jsonPath("$.purpose").value("answer"))
                .andExpect(jsonPath("$.sync_status").value("server_uploaded"))
                .andExpect(jsonPath("$.transcript_status").value("pending"))
                .andExpect(jsonPath("$.deduplicated").value(false))
                .andReturn().getResponse().getContentAsString();
        UUID recordingId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).get("recording_id").asText());

        mockMvc.perform(multipart("/api/v1/recordings")
                        .file(wavFile("retry.wav"))
                        .with(jwtFor(elder))
                        .param("client_recording_id", clientRecordingId.toString())
                        .param("user_id", elder.getId().toString())
                        .param("purpose", "answer")
                        .param("session_id", sessionId.toString())
                        .param("question_id", QUESTION_ID.toString())
                        .param("recorded_at", recordedAt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recording_id").value(recordingId.toString()))
                .andExpect(jsonPath("$.deduplicated").value(true));

        mockMvc.perform(get("/api/v1/recordings/{recordingId}", recordingId)
                        .with(jwtFor(elder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recording_id").value(recordingId.toString()))
                .andExpect(jsonPath("$.session_id").value(sessionId.toString()))
                .andExpect(jsonPath("$.question_id").value(QUESTION_ID.toString()))
                .andExpect(jsonPath("$.transcript_status").value("pending"));

        Path storedRoot = Path.of("build/test-uploads/recordings");
        org.assertj.core.api.Assertions.assertThat(Files.list(storedRoot).findAny()).isPresent();
    }

    @Test
    void diaryRecordingRejectsAnswerReferencesAndOtherUserUpload() throws Exception {
        UserEntity elder = saveUser("recording-diary");
        UserEntity other = saveUser("recording-other");
        UUID clientRecordingId = UUID.randomUUID();
        String recordedAt = Instant.now().minusSeconds(1).toString();

        mockMvc.perform(multipart("/api/v1/recordings")
                        .file(wavFile("diary.m4a", "audio/mp4"))
                        .with(jwtFor(elder))
                        .param("client_recording_id", clientRecordingId.toString())
                        .param("user_id", elder.getId().toString())
                        .param("purpose", "diary")
                        .param("session_id", UUID.randomUUID().toString())
                        .param("recorded_at", recordedAt))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/v1/recordings")
                        .file(wavFile("answer.wav"))
                        .with(jwtFor(elder))
                        .param("client_recording_id", clientRecordingId.toString())
                        .param("user_id", other.getId().toString())
                        .param("purpose", "diary")
                        .param("recorded_at", recordedAt))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/recordings")
                        .file(new MockMultipartFile("audio_file", "answer.webm", "audio/webm", new byte[]{1, 2, 3}))
                        .with(jwtFor(elder))
                        .param("client_recording_id", UUID.randomUUID().toString())
                        .param("user_id", elder.getId().toString())
                        .param("purpose", "diary")
                        .param("recorded_at", recordedAt))
                .andExpect(status().isBadRequest());
    }

    private UUID saveSession(UUID userId) {
        Instant now = Instant.now();
        return sessionRepository.save(new SessionEntity(
                uuidGenerator.generate(),
                userId,
                "cist",
                5,
                "{}",
                false,
                now)).getId();
    }

    private UserEntity saveUser(String prefix) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(
                id,
                prefix + "-" + id + "@example.com",
                null,
                "녹음 사용자",
                "elder",
                LocalDate.of(1945, 1, 1),
                "80s_plus",
                "female",
                null,
                false,
                now,
                now));
    }

    private MockMultipartFile wavFile(String filename) {
        return wavFile(filename, "audio/wav");
    }

    private MockMultipartFile wavFile(String filename, String contentType) {
        return new MockMultipartFile("audio_file", filename, contentType, new byte[]{82, 73, 70, 70});
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt
                .subject(user.getId().toString())
                .claim("role", user.getRole()));
    }
}
