package com.example.haksikmokjang.ai.client;

import com.example.haksikmokjang.ai.moderation.service.ModerationPolicyService;
import com.example.haksikmokjang.ai.moderation.service.ModerationRuleValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiClientProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ModerationPolicyService.class)
            .withBean(ModerationRuleValidationService.class)
            .withUserConfiguration(
                    MockAiClient.class,
                    GeminiAiClient.class,
                    GeminiClientConfiguration.class
            );

    @Test
    void mockProviderSelectsMockClient() {
        contextRunner.withPropertyValues("ai.provider=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(MockAiClient.class);
                });
    }

    @Test
    void geminiProviderSelectsGeminiClient() {
        contextRunner.withPropertyValues(
                        "ai.provider=gemini",
                        "ai.gemini.api-key=test-key",
                        "ai.gemini.model=gemini-test",
                        "ai.gemini.timeout-seconds=1"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(GeminiAiClient.class);
                });
    }

    @Test
    void unsupportedProviderDoesNotSelectClient() {
        contextRunner.withPropertyValues("ai.provider=unknown")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AiClient.class);
                });
    }
}
