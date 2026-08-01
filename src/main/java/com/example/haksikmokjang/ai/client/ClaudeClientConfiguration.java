package com.example.haksikmokjang.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AiClaudeProperties.class)
@ConditionalOnProperty(name = "ai.provider", havingValue = "claude")
public class ClaudeClientConfiguration {

    @Bean
    AnthropicClient anthropicClient(AiClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                // 쿼터 초과(429) 발생 시 SDK가 자동 재시도로 요청을 더 소모하지 않도록 재시도를 끈다.
                // 기존 Gemini 클라이언트도 재시도 없이 첫 실패에서 바로 AiProviderQuotaExceededException을 던졌다.
                .maxRetries(0)
                .build();
    }
}
