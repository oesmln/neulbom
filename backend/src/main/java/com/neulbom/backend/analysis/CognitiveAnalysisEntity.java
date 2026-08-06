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
@Table(name = "cognitive_analyses")
public class CognitiveAnalysisEntity {

    @Id
    private UUID id;

    @Column(name = "transcript_id", nullable = false)
    private UUID transcriptId;

    @Column(name = "acoustic_analysis_id")
    private UUID acousticAnalysisId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType;

    @Column(name = "fusion_mode", nullable = false, length = 20)
    private String fusionMode;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "language_reference_score", precision = 8, scale = 6)
    private BigDecimal languageReferenceScore;

    @Column(name = "screening_reference_score", precision = 8, scale = 6)
    private BigDecimal screeningReferenceScore;

    @Column(length = 30)
    private String label;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cognitive_flags", columnDefinition = "jsonb")
    private String cognitiveFlags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "domain_scores", columnDefinition = "jsonb")
    private String domainScores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_breakdown", columnDefinition = "jsonb")
    private String modelBreakdown;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
