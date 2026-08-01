package com.example.haksikmokjang.ai.recommendation.service;

import com.example.haksikmokjang.ai.client.AiClaudeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiClaudeProperties.class)
public class RestaurantRecommendationConfiguration {
}
