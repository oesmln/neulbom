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
@Table(name = "screening_results")
public class ScreeningResultEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "screening_reference_score", precision = 8, scale = 6)
    private BigDecimal screeningReferenceScore;

    @Column(name = "display_score", precision = 8, scale = 2)
    private BigDecimal displayScore;

    @Column(name = "score_max", precision = 8, scale = 2)
    private BigDecimal scoreMax;

    @Column(name = "score_rate", precision = 8, scale = 6)
    private BigDecimal scoreRate;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "display_label", length = 100)
    private String displayLabel;

    @Column(columnDefinition = "text")
    private String recommendation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "domain_scores", columnDefinition = "jsonb")
    private String domainScores;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
