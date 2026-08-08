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

    protected CognitiveAnalysisEntity() {
    }

    public CognitiveAnalysisEntity(
            UUID id,
            UUID transcriptId,
            UUID acousticAnalysisId,
            UUID userId,
            UUID sessionId,
            UUID questionId,
            String questionType,
            String fusionMode,
            String modelName,
            String modelVersion,
            BigDecimal languageReferenceScore,
            BigDecimal screeningReferenceScore,
            String label,
            String riskLevel,
            String cognitiveFlags,
            String domainScores,
            String modelBreakdown,
            Instant analyzedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.transcriptId = transcriptId;
        this.acousticAnalysisId = acousticAnalysisId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.questionType = questionType;
        this.fusionMode = fusionMode;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.languageReferenceScore = languageReferenceScore;
        this.screeningReferenceScore = screeningReferenceScore;
        this.label = label;
        this.riskLevel = riskLevel;
        this.cognitiveFlags = cognitiveFlags;
        this.domainScores = domainScores;
        this.modelBreakdown = modelBreakdown;
        this.analyzedAt = analyzedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTranscriptId() { return transcriptId; }
    public UUID getAcousticAnalysisId() { return acousticAnalysisId; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getQuestionId() { return questionId; }
    public String getQuestionType() { return questionType; }
    public String getFusionMode() { return fusionMode; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public BigDecimal getLanguageReferenceScore() { return languageReferenceScore; }
    public BigDecimal getScreeningReferenceScore() { return screeningReferenceScore; }
    public String getLabel() { return label; }
    public String getRiskLevel() { return riskLevel; }
    public String getCognitiveFlags() { return cognitiveFlags; }
    public String getDomainScores() { return domainScores; }
    public String getModelBreakdown() { return modelBreakdown; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
