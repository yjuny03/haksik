package com.example.haksikmokjang.ai.recommendation.service;

import com.example.haksikmokjang.ai.client.AiGeminiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiGeminiProperties.class)
public class RestaurantRecommendationConfiguration {
}
