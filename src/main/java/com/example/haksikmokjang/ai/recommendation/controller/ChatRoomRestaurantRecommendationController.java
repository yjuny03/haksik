package com.example.haksikmokjang.ai.recommendation.controller;

import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationRequest;
import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationResponse;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantRecommendationService;
import com.example.haksikmokjang.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/rooms/{chatRoomId}/ai")
public class ChatRoomRestaurantRecommendationController {

    private final RestaurantRecommendationService recommendationService;

    @GetMapping("/restaurant-recommendations")
    public RestaurantRecommendationResponse recommendForChatRoom(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "CHAT_PLACE") String context
    ) {
        return recommendationService.recommendForChatRoom(
                chatRoomId,
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
