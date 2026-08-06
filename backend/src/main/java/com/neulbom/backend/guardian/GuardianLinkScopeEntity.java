package com.neulbom.backend.guardian;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "guardian_link_scopes")
public class GuardianLinkScopeEntity {

    @EmbeddedId
    private GuardianLinkScopeId id;
}
