package com.neulbom.backend.guardian;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianInvitationScopeRepository extends JpaRepository<GuardianInvitationScopeEntity, GuardianInvitationScopeId> {

    List<GuardianInvitationScopeEntity> findAllByIdInvitationId(UUID invitationId);
}
