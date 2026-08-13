package com.neulbom.backend.user.api;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserProfileUpdateRequest(
        @Size(max = 100) String name,
        @Size(max = 30) String phone,
        LocalDate birthDate,
        String ageGroup,
        String gender,
        Integer educationYears,
        Boolean literacy,
        List<String> healthConditions,
        String alcoholUse,
        String smokingStatus,
        String hearingStatus,
        Boolean communicationDifficulty,
        String smartphoneSkill,
        String onboardingStep,
        Boolean onboardingCompleted,
        Boolean baselineCompleted,
        @Size(max = 100) String characterName
) {
}
