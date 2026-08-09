package com.neulbom.backend.guardian;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class GuardianInvitationScopeId implements Serializable {

    @Column(name = "invitation_id", nullable = false)
    private UUID invitationId;

    @Column(nullable = false, length = 20)
    private String scope;

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof GuardianInvitationScopeId other)) {
            return false;
        }
        return Objects.equals(invitationId, other.invitationId) && Objects.equals(scope, other.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invitationId, scope);
    }
}
