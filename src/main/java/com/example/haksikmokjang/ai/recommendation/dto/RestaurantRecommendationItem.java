package com.example.haksikmokjang.ai.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RestaurantRecommendationItem {
    private Integer rank;
    private Long storeId;
    private String storeName;
    private String address;
    private Double latitude;
    private Double longitude;
    private String category;
    private String businessStatus;
    private Integer distanceMeters;
    private String distanceText;
    private Double averageRating;
    private Integer reviewCount;
    private RecommendedMenuResponse recommendedMenu;
    private Long storeImageId;
    private String reason;
    private String matchedPreference;
    private String reviewSummary;
    private String cautionMessage;
    private Double dbScore;
}
