package com.neulbom.backend.game;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<CharacterEntity, UUID> {
}
