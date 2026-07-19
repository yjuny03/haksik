package com.example.haksikmokjang.ai.moderation.dto;

import com.example.haksikmokjang.ai.moderation.domain.AiModerationAnalysis;
import com.example.haksikmokjang.ai.moderation.domain.AiProcessingStatus;
import com.example.haksikmokjang.ai.moderation.domain.AutoHideBasis;
import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AiModerationAnalysisResponse {

    private ModerationCategory category;
    private ModerationDecision decision;
    private AiProcessingStatus processingStatus;
    private BigDecimal riskScore;
    private BigDecimal confidenceScore;
    private List<String> policyCodes;
    private boolean containsPersonalInfo;
    private List<String> personalInfoTypes;
    private String reasonSummary;
    private String modelName;
    private String policyVersion;
    private boolean ruleMatched;
    private AutoHideBasis autoHideBasis;
    private LocalDateTime analyzedAt;
    private LocalDateTime restoredAt;
    private String restoreReason;

    public static AiModerationAnalysisResponse from(AiModerationAnalysis analysis) {
        return AiModerationAnalysisResponse.builder()
                .category(analysis.getCategory())
                .decision(analysis.getDecision())
                .processingStatus(analysis.getProcessingStatus())
                .riskScore(analysis.getRiskScore())
                .confidenceScore(analysis.getConfidenceScore())
                .policyCodes(analysis.getPolicyCodeList())
                .containsPersonalInfo(analysis.isContainsPersonalInfo())
                .personalInfoTypes(analysis.getPersonalInfoTypeList())
                .reasonSummary(analysis.getReasonSummary())
                .modelName(analysis.getModelName())
                .policyVersion(analysis.getPolicyVersion())
                .ruleMatched(analysis.isRuleMatched())
                .autoHideBasis(analysis.getAutoHideBasis())
                .analyzedAt(analysis.getAnalyzedAt())
                .restoredAt(analysis.getRestoredAt())
                .restoreReason(analysis.getRestoreReason())
                .build();
    }
}
