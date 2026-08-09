package com.neulbom.backend.guardian;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuardianInvitationRepository extends JpaRepository<GuardianInvitationEntity, UUID> {

    Optional<GuardianInvitationEntity> findByCodeHash(String codeHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from GuardianInvitationEntity invitation where invitation.codeHash = :codeHash")
    Optional<GuardianInvitationEntity> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
