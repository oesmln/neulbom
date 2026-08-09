package com.neulbom.backend.guardian;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class GuardianLinkScopeId implements Serializable {

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(nullable = false, length = 20)
    private String scope;

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof GuardianLinkScopeId other)) {
            return false;
        }
        return Objects.equals(linkId, other.linkId) && Objects.equals(scope, other.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linkId, scope);
    }
}
