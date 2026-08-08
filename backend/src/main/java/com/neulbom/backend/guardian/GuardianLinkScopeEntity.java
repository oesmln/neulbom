package com.neulbom.backend.guardian;

import java.util.UUID;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "guardian_link_scopes")
public class GuardianLinkScopeEntity {

    @EmbeddedId
    private GuardianLinkScopeId id;

    protected GuardianLinkScopeEntity() {
    }

    public GuardianLinkScopeEntity(UUID linkId, String scope) {
        this.id = new GuardianLinkScopeId(linkId, scope);
    }

    public GuardianLinkScopeId getId() {
        return id;
    }
}
