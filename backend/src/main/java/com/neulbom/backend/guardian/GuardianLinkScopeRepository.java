package com.neulbom.backend.guardian;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianLinkScopeRepository extends JpaRepository<GuardianLinkScopeEntity, GuardianLinkScopeId> {

    List<GuardianLinkScopeEntity> findAllByIdLinkId(UUID linkId);
}
