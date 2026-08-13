package com.neulbom.backend.counseling;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ResourceNotFoundException;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.counseling.api.CounselingAppointmentCreateRequest;
import com.neulbom.backend.counseling.api.CounselingAppointmentResponse;
import com.neulbom.backend.counseling.api.CounselingAppointmentUpdateRequest;
import com.neulbom.backend.counseling.api.CounselingAppointmentsResponse;
import com.neulbom.backend.guardian.GuardianAccessService;
import com.neulbom.backend.notification.NotificationService;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CounselingAppointmentService {

    private static final Set<String> CONSULTATION_TYPES = Set.of(
            "cognitive_screening", "neurology", "counseling", "other");

    private final CounselingAppointmentRepository appointmentRepository;
    private final CounselingCenterRepository centerRepository;
    private final UserRepository userRepository;
    private final GuardianAccessService guardianAccessService;
    private final NotificationService notificationService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public CounselingAppointmentService(
            CounselingAppointmentRepository appointmentRepository,
            CounselingCenterRepository centerRepository,
            UserRepository userRepository,
            GuardianAccessService guardianAccessService,
            NotificationService notificationService,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.appointmentRepository = appointmentRepository;
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.guardianAccessService = guardianAccessService;
        this.notificationService = notificationService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public CounselingAppointmentResponse create(UUID guardianId, CounselingAppointmentCreateRequest request) {
        requireGuardian(guardianId);
        requirePrivacyAgreement(request.privacyAgreed());
        guardianAccessService.requireAccess(guardianId, request.elderId(), "summary");
        CounselingCenterEntity center = centerRepository.findById(request.centerId())
                .filter(CounselingCenterEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("상담 기관을 찾을 수 없습니다."));
        String consultationType = normalizeConsultationType(request.consultationType());
        Instant now = clock.instant();
        CounselingAppointmentEntity appointment = new CounselingAppointmentEntity(
                uuidGenerator.generate(),
                guardianId,
                request.elderId(),
                center.getId(),
                request.appointmentAt(),
                consultationType,
                CounselingAppointmentEntity.REQUESTED,
                normalizeNote(request.note()),
                true,
                now,
                now);
        appointmentRepository.save(appointment);
        notificationService.notifyAppointmentUpdated(guardianId, appointment.getId(), "requested");
        return toResponse(appointment, center);
    }

    @Transactional(readOnly = true)
    public CounselingAppointmentsResponse list(UUID guardianId) {
        requireGuardian(guardianId);
        List<CounselingAppointmentResponse> appointments = appointmentRepository
                .findAllByGuardianIdOrderByAppointmentAtDesc(guardianId)
                .stream()
                .map(this::toResponse)
                .toList();
        return new CounselingAppointmentsResponse(appointments, appointments.size());
    }

    @Transactional
    public CounselingAppointmentResponse update(
            UUID guardianId,
            UUID appointmentId,
            CounselingAppointmentUpdateRequest request
    ) {
        CounselingAppointmentEntity appointment = ownedAppointment(guardianId, appointmentId);
        ensureEditable(appointment);
        Instant appointmentAt = request.appointmentAt() == null ? appointment.getAppointmentAt() : request.appointmentAt();
        String consultationType = request.consultationType() == null
                ? appointment.getConsultationType() : normalizeConsultationType(request.consultationType());
        String note = request.note() == null ? appointment.getNote() : normalizeNote(request.note());
        appointment.update(appointmentAt, consultationType, note, clock.instant());
        appointmentRepository.save(appointment);
        notificationService.notifyAppointmentUpdated(guardianId, appointment.getId(), "updated");
        return toResponse(appointment);
    }

    @Transactional
    public void cancel(UUID guardianId, UUID appointmentId) {
        CounselingAppointmentEntity appointment = ownedAppointment(guardianId, appointmentId);
        ensureEditable(appointment);
        appointment.cancel(clock.instant());
        appointmentRepository.save(appointment);
        notificationService.notifyAppointmentUpdated(guardianId, appointment.getId(), "cancelled");
    }

    private CounselingAppointmentEntity ownedAppointment(UUID guardianId, UUID appointmentId) {
        requireGuardian(guardianId);
        return appointmentRepository.findByIdAndGuardianId(appointmentId, guardianId)
                .orElseThrow(() -> new ResourceNotFoundException("예약을 찾을 수 없습니다."));
    }

    private void ensureEditable(CounselingAppointmentEntity appointment) {
        if (!CounselingAppointmentEntity.REQUESTED.equals(appointment.getStatus())
                && !CounselingAppointmentEntity.CONFIRMED.equals(appointment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "변경할 수 없는 예약입니다.", "요청 또는 확정 상태의 예약만 변경할 수 있습니다.");
        }
    }

    private UserEntity requireGuardian(UUID guardianId) {
        UserEntity user = userRepository.findById(guardianId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 정보를 찾을 수 없습니다."));
        if (!"guardian".equals(user.getRole())) {
            throw new AccessDeniedException("보호자만 상담 예약을 이용할 수 있습니다.");
        }
        return user;
    }

    private String normalizeConsultationType(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!CONSULTATION_TYPES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "consultation_type 허용값을 확인하세요.");
        }
        return normalized;
    }

    private void requirePrivacyAgreement(Boolean privacyAgreed) {
        if (!Boolean.TRUE.equals(privacyAgreed)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "예약 동의가 필요합니다.", "개인정보 제공 및 예약 동의 후 신청할 수 있습니다.");
        }
    }

    private String normalizeNote(String note) {
        if (note == null) return null;
        String normalized = note.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private CounselingAppointmentResponse toResponse(CounselingAppointmentEntity appointment) {
        CounselingCenterEntity center = centerRepository.findById(appointment.getCenterId()).orElse(null);
        return toResponse(appointment, center);
    }

    private CounselingAppointmentResponse toResponse(CounselingAppointmentEntity appointment, CounselingCenterEntity center) {
        return new CounselingAppointmentResponse(
                appointment.getId(),
                appointment.getGuardianId(),
                appointment.getElderId(),
                appointment.getCenterId(),
                center == null ? null : center.getName(),
                center == null ? null : center.getAddress(),
                appointment.getAppointmentAt(),
                appointment.getConsultationType(),
                appointment.getStatus(),
                appointment.getNote(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt());
    }
}
