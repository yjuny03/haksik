package com.example.haksikmokjang.ai.moderation.domain;

public enum AutoHideBasis {
    SERVER_RULE_MATCHED,
    AI_HIGH_CONFIDENCE,
    EXCLUDED_CONTEXT_REVIEW_REQUIRED,
    HIGH_RISK_REVIEW_REQUIRED,
    REVIEW_REQUIRED,
    NOT_MATCHED,
    NOT_ELIGIBLE_CATEGORY,
    AI_FAILED
}
