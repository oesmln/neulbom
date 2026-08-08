package com.neulbom.backend.user;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, UUID> {
}
