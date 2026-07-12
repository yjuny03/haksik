package com.example.haksikmokjang.ai.moderation.domain;

import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import com.example.haksikmokjang.global.entity.BaseEntity;
import com.example.haksikmokjang.report.domain.Report;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "AI_MODERATION_ANALYSIS",
        uniqueConstraints = @UniqueConstraint(name = "UK_AI_MODERATION_REPORT", columnNames = "report_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiModerationAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_moderation_analysis_id")
    private Long aiModerationAnalysisId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ModerationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private ModerationDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    private AiProcessingStatus processingStatus;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal riskScore;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "policy_codes", nullable = false, length = 500)
    private String policyCodes;

    @Column(name = "contains_personal_info", nullable = false)
    private boolean containsPersonalInfo;

    @Column(name = "personal_info_types", nullable = false, length = 200)
    private String personalInfoTypes;

    @Column(name = "reason_summary", nullable = false, length = 500)
    private String reasonSummary;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Column(name = "rule_matched", nullable = false)
    private boolean ruleMatched;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "restored_at")
    private LocalDateTime restoredAt;

    @Column(name = "restore_reason", length = 500)
    private String restoreReason;

    public AiModerationAnalysis(Report report, ModerationResult result, boolean ruleMatched) {
        this.report = report;
        this.category = result.category();
        this.decision = result.decision();
        this.processingStatus = statusFor(result.decision());
        this.riskScore = result.riskScore();
        this.confidenceScore = result.confidenceScore();
        this.policyCodes = String.join(",", result.policyCodes());
        this.containsPersonalInfo = result.containsPersonalInfo();
        this.personalInfoTypes = String.join(",", result.personalInfoTypes());
        this.reasonSummary = result.reasonSummary();
        this.modelName = result.modelName();
        this.policyVersion = result.policyVersion();
        this.ruleMatched = ruleMatched;
        this.analyzedAt = LocalDateTime.now();
    }

    public static AiModerationAnalysis failed(Report report, String reasonSummary, String modelName) {
        AiModerationAnalysis analysis = new AiModerationAnalysis();
        analysis.report = report;
        analysis.category = ModerationCategory.OTHER;
        analysis.decision = ModerationDecision.REVIEW_REQUIRED;
        analysis.processingStatus = AiProcessingStatus.FAILED;
        analysis.riskScore = BigDecimal.ZERO;
        analysis.confidenceScore = BigDecimal.ZERO;
        analysis.policyCodes = "AI-ANALYSIS-FAILED";
        analysis.containsPersonalInfo = false;
        analysis.personalInfoTypes = "";
        analysis.reasonSummary = reasonSummary;
        analysis.modelName = modelName;
        analysis.policyVersion = "MODERATION_POLICY_V1";
        analysis.ruleMatched = false;
        analysis.analyzedAt = LocalDateTime.now();
        return analysis;
    }

    private AiProcessingStatus statusFor(ModerationDecision decision) {
        return switch (decision) {
            case AUTO_HIDE -> AiProcessingStatus.SUCCEEDED;
            case REVIEW_REQUIRED -> AiProcessingStatus.REVIEW_REQUIRED;
            case NO_ACTION -> AiProcessingStatus.NO_ACTION;
        };
    }

    public List<String> getPolicyCodeList() {
        return policyCodes.isBlank() ? List.of() : List.of(policyCodes.split(","));
    }

    public List<String> getPersonalInfoTypeList() {
        return personalInfoTypes.isBlank() ? List.of() : List.of(personalInfoTypes.split(","));
    }

    public void markAutoProcessed() {
        this.processingStatus = AiProcessingStatus.AUTO_PROCESSED;
    }

    public void markReviewRequired() {
        this.processingStatus = AiProcessingStatus.REVIEW_REQUIRED;
    }

    public void markRestored(String restoreReason) {
        this.processingStatus = AiProcessingStatus.RESTORED;
        this.restoredAt = LocalDateTime.now();
        this.restoreReason = restoreReason;
    }
}
