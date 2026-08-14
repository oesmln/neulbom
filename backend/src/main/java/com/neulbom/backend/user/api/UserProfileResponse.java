package com.neulbom.backend.user.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserProfileResponse(
        UUID userId,
        String name,
        String role,
        LocalDate birthDate,
        String ageGroup,
        String gender,
        String phone,
        Integer educationYears,
        Boolean literacy,
        List<String> healthConditions,
        String alcoholUse,
        String smokingStatus,
        String hearingStatus,
        Boolean communicationDifficulty,
        String smartphoneSkill,
        boolean profileCompleted,
        String onboardingStep,
        boolean onboardingCompleted,
        boolean baselineCompleted,
        String characterName,
        Instant createdAt,
        Instant updatedAt
) {
}
