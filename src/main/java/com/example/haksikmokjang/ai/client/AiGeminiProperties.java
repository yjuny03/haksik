package com.example.haksikmokjang.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.gemini")
public record AiGeminiProperties(
        String apiKey,
        String model,
        int timeoutSeconds,
        String baseUrl
) {
    public AiGeminiProperties {
        model = model == null || model.isBlank() ? "gemini-3.5-flash" : model;
        timeoutSeconds = timeoutSeconds <= 0 ? 10 : timeoutSeconds;
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com"
                : baseUrl;
    }
}
