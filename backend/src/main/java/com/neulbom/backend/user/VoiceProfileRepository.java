package com.neulbom.backend.user;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceProfileRepository extends JpaRepository<VoiceProfileEntity, String> {

    List<VoiceProfileEntity> findAllByActiveTrueAndLanguageOrderByRecommendedForElderDescNameAsc(String language);
}
