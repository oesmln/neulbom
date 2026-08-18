package com.neulbom.backend.game;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CharacterRepository extends JpaRepository<CharacterEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select character from CharacterEntity character where character.userId = :userId")
    Optional<CharacterEntity> findByUserIdForUpdate(@Param("userId") UUID userId);
}
