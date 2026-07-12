package com.example.haksikmokjang.ai.moderation.dto;

public record ModerationPromptRequest(
        Long reportId,
        String targetType,
        String reportReason,
        String content
) {
}
