package com.neulbom.backend.guardian;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "guardian_invitation_scopes")
public class GuardianInvitationScopeEntity {

    @EmbeddedId
    private GuardianInvitationScopeId id;
}
