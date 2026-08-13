package com.neulbom.backend.counseling;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "counseling_appointments")
public class CounselingAppointmentEntity {

    public static final String REQUESTED = "requested";
    public static final String CONFIRMED = "confirmed";
    public static final String CANCELLED = "cancelled";
    public static final String COMPLETED = "completed";

    @Id
    private UUID id;

    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;

    @Column(name = "elder_id", nullable = false)
    private UUID elderId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "appointment_at", nullable = false)
    private Instant appointmentAt;

    @Column(name = "consultation_type", nullable = false, length = 40)
    private String consultationType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 1000)
    private String note;

    @Column(name = "privacy_agreed", nullable = false)
    private boolean privacyAgreed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CounselingAppointmentEntity() {
    }

    public CounselingAppointmentEntity(
            UUID id,
            UUID guardianId,
            UUID elderId,
            UUID centerId,
            Instant appointmentAt,
            String consultationType,
            String status,
            String note,
            boolean privacyAgreed,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.guardianId = guardianId;
        this.elderId = elderId;
        this.centerId = centerId;
        this.appointmentAt = appointmentAt;
        this.consultationType = consultationType;
        this.status = status;
        this.note = note;
        this.privacyAgreed = privacyAgreed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getGuardianId() { return guardianId; }
    public UUID getElderId() { return elderId; }
    public UUID getCenterId() { return centerId; }
    public Instant getAppointmentAt() { return appointmentAt; }
    public String getConsultationType() { return consultationType; }
    public String getStatus() { return status; }
    public String getNote() { return note; }
    public boolean isPrivacyAgreed() { return privacyAgreed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(Instant appointmentAt, String consultationType, String note, Instant updatedAt) {
        this.appointmentAt = appointmentAt;
        this.consultationType = consultationType;
        this.note = note;
        this.updatedAt = updatedAt;
    }

    public void cancel(Instant cancelledAt) {
        this.status = CANCELLED;
        this.updatedAt = cancelledAt;
    }
}
