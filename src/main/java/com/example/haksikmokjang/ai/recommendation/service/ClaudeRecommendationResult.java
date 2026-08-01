package com.example.haksikmokjang.ai.recommendation.service;

import java.util.List;

public record ClaudeRecommendationResult(
        List<Item> recommendations
) {
    public record Item(
            Long recommendedStoreId,
            Long recommendedMenuId,
            Integer rank,
            String reason,
            String matchedPreference,
            String distanceText,
            String reviewSummary,
            String cautionMessage
    ) {
    }
}
