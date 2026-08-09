package com.neulbom.backend.guardian;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportExportRepository extends JpaRepository<ReportExportEntity, UUID> {

    Optional<ReportExportEntity> findByRequestKey(String requestKey);
}
