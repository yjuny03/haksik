package com.example.haksikmokjang.ai.moderation.dto;

import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;

import java.math.BigDecimal;
import java.util.List;

public record ModerationResult(
        String policyVersion,
        ModerationCategory category,
        ModerationDecision decision,
        BigDecimal riskScore,
        BigDecimal confidenceScore,
        List<String> policyCodes,
        boolean containsPersonalInfo,
        List<String> personalInfoTypes,
        boolean ruleEvidenceRequired,
        String reasonSummary,
        List<String> evidence,
        List<String> missingContext,
        ModerationCategory alternativeCategory,
        String alternativeReason,
        String modelName
) {
    public ModerationResult(
            String policyVersion,
            ModerationCategory category,
            ModerationDecision decision,
            BigDecimal riskScore,
            BigDecimal confidenceScore,
            List<String> policyCodes,
            boolean containsPersonalInfo,
            List<String> personalInfoTypes,
            boolean ruleEvidenceRequired,
            String reasonSummary,
            String modelName
    ) {
        this(policyVersion, category, decision, riskScore, confidenceScore, policyCodes,
                containsPersonalInfo, personalInfoTypes, ruleEvidenceRequired, reasonSummary,
                List.of(), List.of(), null, null, modelName);
    }
}
