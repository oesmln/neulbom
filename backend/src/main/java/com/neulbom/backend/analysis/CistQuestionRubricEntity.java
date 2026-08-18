package com.neulbom.backend.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cist_question_rubrics")
public class CistQuestionRubricEntity {

    @Id
    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "rule_type", nullable = false, length = 40)
    private String ruleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_values", nullable = false, columnDefinition = "jsonb")
    private String expectedValues;

    @Column(name = "max_score", nullable = false, precision = 8, scale = 4)
    private BigDecimal maxScore;

    @Column(name = "rubric_version", nullable = false, length = 50)
    private String rubricVersion;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CistQuestionRubricEntity() {
    }

    public CistQuestionRubricEntity(
            UUID questionId,
            String ruleType,
            String expectedValues,
            BigDecimal maxScore,
            String rubricVersion,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.questionId = questionId;
        this.ruleType = ruleType;
        this.expectedValues = expectedValues;
        this.maxScore = maxScore;
        this.rubricVersion = rubricVersion;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getQuestionId() { return questionId; }
    public String getRuleType() { return ruleType; }
    public String getExpectedValues() { return expectedValues; }
    public BigDecimal getMaxScore() { return maxScore; }
    public String getRubricVersion() { return rubricVersion; }
    public boolean isActive() { return active; }
}
