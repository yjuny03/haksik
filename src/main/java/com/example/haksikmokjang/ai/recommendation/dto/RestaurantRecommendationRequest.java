package com.example.haksikmokjang.ai.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RestaurantRecommendationRequest {
    private Double lat;
    private Double lng;
    private Double radiusKm;
    private String category;
    private Integer limit;
    private String context;
}
