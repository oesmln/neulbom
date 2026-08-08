package com.neulbom.backend.recording;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.file.FileUploadValidator;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.guardian.GuardianAccessService;
import com.neulbom.backend.recording.api.RecordingStatusResponse;
import com.neulbom.backend.recording.api.RecordingUploadResponse;
import com.neulbom.backend.session.QuestionEntity;
import com.neulbom.backend.session.QuestionRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RecordingService {

    private static final Set<String> PURPOSES = Set.of(RecordingEntity.ANSWER, RecordingEntity.DIARY);
    private static final Set<String> DEVICE_STATUSES = Set.of("device_saved", "server_pending");

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final RecordingRepository recordingRepository;
    private final TranscriptRepository transcriptRepository;
    private final GuardianAccessService guardianAccessService;
    private final FileUploadValidator fileUploadValidator;
    private final RecordingStorage recordingStorage;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public RecordingService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            QuestionRepository questionRepository,
            RecordingRepository recordingRepository,
            TranscriptRepository transcriptRepository,
            GuardianAccessService guardianAccessService,
            FileUploadValidator fileUploadValidator,
            RecordingStorage recordingStorage,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.recordingRepository = recordingRepository;
        this.transcriptRepository = transcriptRepository;
        this.guardianAccessService = guardianAccessService;
        this.fileUploadValidator = fileUploadValidator;
        this.recordingStorage = recordingStorage;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public RecordingUploadResponse upload(
            UUID authenticatedUserId,
            MultipartFile audioFile,
            UUID clientRecordingId,
            UUID requestedUserId,
            String purpose,
            UUID sessionId,
            UUID questionId,
            Instant recordedAt,
            String deviceStatus
    ) {
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new AccessDeniedException("본인 녹음만 업로드할 수 있습니다.");
        }
        UserEntity user = activeElder(requestedUserId);
        RecordingEntity existing = recordingRepository.findByClientRecordingId(clientRecordingId).orElse(null);
        if (existing != null) {
            if (!existing.getUserId().equals(user.getId())) {
                throw new AccessDeniedException("다른 사용자의 녹음 ID입니다.");
            }
            return toUploadResponse(existing, true);
        }

        validatePurpose(purpose, sessionId, questionId);
        if (deviceStatus != null && !DEVICE_STATUSES.contains(deviceStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "device_status 허용값을 확인하세요.");
        }
        if (recordedAt == null || recordedAt.isAfter(clock.instant().plusSeconds(60))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "녹음 일시가 올바르지 않습니다.", "recorded_at을 확인하세요.");
        }
        fileUploadValidator.validate(audioFile);
        validatePurposeReferences(user.getId(), purpose, sessionId, questionId);

        UUID recordingId = uuidGenerator.generate();
        String storageKey = recordingStorage.store(recordingId, audioFile);
        Instant now = clock.instant();
        RecordingEntity recording = new RecordingEntity(
                recordingId,
                clientRecordingId,
                user.getId(),
                purpose,
                sessionId,
                questionId,
                storageKey,
                truncate(audioFile.getOriginalFilename(), 255),
                metadata(audioFile),
                audioFile.getContentType(),
                audioFile.getSize(),
                recordedAt,
                now);
        recordingRepository.save(recording);
        return toUploadResponse(recording, false);
    }

    @Transactional(readOnly = true)
    public RecordingStatusResponse getStatus(UUID authenticatedUserId, UUID recordingId) {
        RecordingEntity recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResourceNotFoundException("녹음 정보를 찾을 수 없습니다."));
        authorizeRead(authenticatedUserId, recording);
        UUID transcriptId = transcriptRepository.findByRecordingId(recording.getId())
                .map(TranscriptEntity::getId)
                .orElse(null);
        return new RecordingStatusResponse(
                recording.getId(),
                recording.getClientRecordingId(),
                recording.getPurpose(),
                recording.getSessionId(),
                recording.getQuestionId(),
                recording.getSyncStatus(),
                recording.getTranscriptStatus(),
                transcriptId,
                null,
                null,
                null,
                recording.getUpdatedAt());
    }

    private UserEntity activeElder(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
        if (!"elder".equals(user.getRole())) {
            throw new AccessDeniedException("고령자 계정만 녹음을 업로드할 수 있습니다.");
        }
        return user;
    }

    private void validatePurpose(String purpose, UUID sessionId, UUID questionId) {
        if (!PURPOSES.contains(purpose)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "purpose는 answer 또는 diary여야 합니다.");
        }
        if (RecordingEntity.ANSWER.equals(purpose) && (sessionId == null || questionId == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "문항 답변 녹음 정보가 부족합니다.", "answer 목적에는 session_id와 question_id가 필요합니다.");
        }
        if (RecordingEntity.DIARY.equals(purpose) && (sessionId != null || questionId != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "음성 일기 녹음 정보가 올바르지 않습니다.", "diary 목적에는 session_id와 question_id를 보내지 않습니다.");
        }
    }

    private void validatePurposeReferences(UUID userId, String purpose, UUID sessionId, UUID questionId) {
        if (!RecordingEntity.ANSWER.equals(purpose)) {
            return;
        }
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("세션을 찾을 수 없습니다."));
        if (!userId.equals(session.getUserId())) {
            throw new AccessDeniedException("본인 세션에만 녹음을 연결할 수 있습니다.");
        }
        QuestionEntity question = questionRepository.findById(questionId)
                .filter(QuestionEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("질문을 찾을 수 없습니다."));
        if (!(session.getSessionType().equals(question.getSessionType()) || "mixed".equals(session.getSessionType()))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "세션 문항이 아닙니다.", "session_id와 question_id를 확인하세요.");
        }
    }

    private void authorizeRead(UUID authenticatedUserId, RecordingEntity recording) {
        if (authenticatedUserId.equals(recording.getUserId())) {
            return;
        }
        String scope = RecordingEntity.DIARY.equals(recording.getPurpose()) ? "diary" : "screening";
        guardianAccessService.requireAccess(authenticatedUserId, recording.getUserId(), scope);
    }

    private RecordingUploadResponse toUploadResponse(RecordingEntity recording, boolean deduplicated) {
        return new RecordingUploadResponse(
                recording.getId(),
                recording.getClientRecordingId(),
                recording.getPurpose(),
                recording.getSyncStatus(),
                recording.getTranscriptStatus(),
                recording.getAnalysisStatus(),
                deduplicated);
    }

    private String metadata(MultipartFile file) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "content_type", file.getContentType(),
                    "size_bytes", file.getSize()));
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "녹음 메타데이터 저장에 실패했습니다.", "잠시 후 다시 시도하세요.");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
