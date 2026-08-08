package com.neulbom.backend.guardian;

import java.util.UUID;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "guardian_invitation_scopes")
public class GuardianInvitationScopeEntity {

    @EmbeddedId
    private GuardianInvitationScopeId id;

    protected GuardianInvitationScopeEntity() {
    }

    public GuardianInvitationScopeEntity(UUID invitationId, String scope) {
        this.id = new GuardianInvitationScopeId(invitationId, scope);
    }

    public GuardianInvitationScopeId getId() {
        return id;
    }
}
