package com.example.haksikmokjang.ai.moderation.domain;

public enum AiProcessingStatus {
    SUCCEEDED,
    AUTO_PROCESSED,
    REVIEW_REQUIRED,
    NO_ACTION,
    FAILED,
    RESTORED
}
