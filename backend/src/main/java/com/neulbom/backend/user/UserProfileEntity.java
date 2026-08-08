package com.neulbom.backend.user;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "education_years")
    private Integer educationYears;

    private Boolean literacy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "health_conditions", columnDefinition = "jsonb")
    private String healthConditions;

    @Column(name = "alcohol_use", length = 20)
    private String alcoholUse;

    @Column(name = "smoking_status", length = 20)
    private String smokingStatus;

    @Column(name = "hearing_status", length = 20)
    private String hearingStatus;

    @Column(name = "communication_difficulty")
    private Boolean communicationDifficulty;

    @Column(name = "smartphone_skill", length = 20)
    private String smartphoneSkill;

    protected UserProfileEntity() {
    }

    public UserProfileEntity(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Integer getEducationYears() {
        return educationYears;
    }

    public Boolean getLiteracy() {
        return literacy;
    }

    public String getHealthConditions() {
        return healthConditions;
    }

    public String getAlcoholUse() {
        return alcoholUse;
    }

    public String getSmokingStatus() {
        return smokingStatus;
    }

    public String getHearingStatus() {
        return hearingStatus;
    }

    public Boolean getCommunicationDifficulty() {
        return communicationDifficulty;
    }

    public String getSmartphoneSkill() {
        return smartphoneSkill;
    }

    public void update(
            Integer educationYears,
            Boolean literacy,
            String healthConditions,
            String alcoholUse,
            String smokingStatus,
            String hearingStatus,
            Boolean communicationDifficulty,
            String smartphoneSkill
    ) {
        this.educationYears = educationYears;
        this.literacy = literacy;
        this.healthConditions = healthConditions;
        this.alcoholUse = alcoholUse;
        this.smokingStatus = smokingStatus;
        this.hearingStatus = hearingStatus;
        this.communicationDifficulty = communicationDifficulty;
        this.smartphoneSkill = smartphoneSkill;
    }
}
