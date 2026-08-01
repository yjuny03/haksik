package com.example.haksikmokjang.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.claude")
public record AiClaudeProperties(
        String apiKey,
        String model,
        int timeoutSeconds,
        String baseUrl
) {
    public AiClaudeProperties {
        model = model == null || model.isBlank() ? "claude-sonnet-5" : model;
        timeoutSeconds = timeoutSeconds <= 0 ? 10 : timeoutSeconds;
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://api.anthropic.com"
                : baseUrl;
    }
}
