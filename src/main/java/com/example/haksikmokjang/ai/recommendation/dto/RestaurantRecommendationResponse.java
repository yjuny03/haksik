package com.example.haksikmokjang.ai.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RestaurantRecommendationResponse {
    private String source;
    private LocationResponse center;
    private Double radiusKm;
    private Double expandedRadiusKm;
    private String preferredFoodCategory;
    private Map<String, Long> chatPreferredFoodCategoryCounts;
    private String majorityPreferredFoodCategory;
    private List<RestaurantRecommendationItem> recommendations;
    private String fallbackReason;
}
