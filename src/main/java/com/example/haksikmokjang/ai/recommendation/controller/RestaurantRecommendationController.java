package com.example.haksikmokjang.ai.recommendation.controller;

import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationRequest;
import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationResponse;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantRecommendationService;
import com.example.haksikmokjang.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/restaurants")
public class RestaurantRecommendationController {

    private final RestaurantRecommendationService recommendationService;

    @GetMapping("/recommendations")
    public RestaurantRecommendationResponse recommend(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String context
    ) {
        return recommendationService.recommend(
                userDetails.getMember(),
                RestaurantRecommendationRequest.builder()
                        .lat(lat)
                        .lng(lng)
                        .radiusKm(radiusKm)
                        .category(category)
                        .limit(limit)
                        .context(context)
                        .build()
        );
    }

}
