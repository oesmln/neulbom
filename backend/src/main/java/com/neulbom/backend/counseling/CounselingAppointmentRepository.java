package com.neulbom.backend.counseling;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CounselingAppointmentRepository extends JpaRepository<CounselingAppointmentEntity, UUID> {

    List<CounselingAppointmentEntity> findAllByGuardianIdOrderByAppointmentAtDesc(UUID guardianId);

    Optional<CounselingAppointmentEntity> findByIdAndGuardianId(UUID id, UUID guardianId);
}
