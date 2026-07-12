package com.example.haksikmokjang.ai.recommendation.service;

public record RestaurantScore(
        double total,
        double distanceScore,
        double preferredCategoryScore,
        double requestedCategoryScore,
        double ratingScore,
        double reviewCountScore,
        double recentReviewScore,
        double menuScore,
        double chatPreferenceScore,
        double majorityPreferenceScore,
        double recommendedMenuScore
) {
}
