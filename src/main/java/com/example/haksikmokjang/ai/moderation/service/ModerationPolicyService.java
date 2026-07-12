package com.example.haksikmokjang.ai.moderation.service;

import com.example.haksikmokjang.ai.moderation.ModerationPolicyConstants;
import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

@Service
public class ModerationPolicyService {

    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.90");
    private static final BigDecimal SEVERE_ABUSE_THRESHOLD = new BigDecimal("0.95");
    private static final Set<ModerationCategory> DEFAULT_AUTO_HIDE_CATEGORIES = EnumSet.of(
            ModerationCategory.SPAM_AD,
            ModerationCategory.PERSONAL_INFO,
            ModerationCategory.FLOODING
    );

    public boolean canAutoHide(ModerationResult result, boolean ruleMatched) {
        if (!isValid(result)
                || result.decision() != ModerationDecision.AUTO_HIDE
                || !ruleMatched
                || !result.ruleEvidenceRequired()
                || result.policyCodes() == null
                || result.policyCodes().isEmpty()) {
            return false;
        }

        if (DEFAULT_AUTO_HIDE_CATEGORIES.contains(result.category())) {
            return meetsBothThresholds(result, DEFAULT_THRESHOLD);
        }

        return result.category() == ModerationCategory.ABUSE
                && result.policyCodes().contains("ABUSE-SEVERE")
                && meetsBothThresholds(result, SEVERE_ABUSE_THRESHOLD);
    }

    public boolean isValid(ModerationResult result) {
        if (result == null
                || !ModerationPolicyConstants.POLICY_VERSION.equals(result.policyVersion())
                || result.category() == null
                || result.decision() == null
                || result.riskScore() == null
                || result.confidenceScore() == null
                || result.policyCodes() == null
                || result.personalInfoTypes() == null
                || result.reasonSummary() == null
                || result.reasonSummary().isBlank()
                || result.reasonSummary().length() > 500
                || !isScore(result.riskScore())
                || !isScore(result.confidenceScore())) {
            return false;
        }

        if (result.category() == ModerationCategory.SAFE) {
            return result.decision() != ModerationDecision.AUTO_HIDE
                    && result.riskScore().compareTo(new BigDecimal("0.19")) <= 0;
        }

        if (result.category() == ModerationCategory.PERSONAL_INFO
                && (!result.containsPersonalInfo() || result.personalInfoTypes().isEmpty())) {
            return false;
        }

        return result.containsPersonalInfo() || result.personalInfoTypes().isEmpty();
    }

    private boolean isScore(BigDecimal score) {
        return score.compareTo(BigDecimal.ZERO) >= 0
                && score.compareTo(BigDecimal.ONE) <= 0;
    }

    private boolean meetsBothThresholds(ModerationResult result, BigDecimal threshold) {
        return result.riskScore() != null
                && result.confidenceScore() != null
                && result.riskScore().compareTo(threshold) >= 0
                && result.confidenceScore().compareTo(threshold) >= 0;
    }
}
