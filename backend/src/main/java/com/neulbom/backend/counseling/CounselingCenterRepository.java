package com.neulbom.backend.counseling;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CounselingCenterRepository extends JpaRepository<CounselingCenterEntity, java.util.UUID> {

    List<CounselingCenterEntity> findAllByActiveTrueOrderByNameAsc();
}
