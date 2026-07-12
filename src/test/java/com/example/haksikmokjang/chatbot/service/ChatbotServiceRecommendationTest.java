package com.example.haksikmokjang.chatbot.service;

import com.example.haksikmokjang.ai.recommendation.service.RestaurantRecommendationService;
import com.example.haksikmokjang.member.core.domain.Member;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotServiceRecommendationTest {

    @Test
    void restaurantRecommendationIntentDelegatesToPersonalRecommendationService() {
        RestaurantRecommendationService recommendationService = mock(RestaurantRecommendationService.class);
        ChatbotService chatbotService = new ChatbotService(recommendationService);
        Member member = Member.builder().loginId("user1").build();
        when(recommendationService.recommendForChatbot(member, "한식", 37.631668, 127.054045))
                .thenReturn("오늘은 내 선호도 기준으로 인덕 제육하우스를 추천해요.");

        String answer = chatbotService.getAiResponse("오늘의 음식 추천해줘", "한식", member, 37.631668, 127.054045);

        assertThat(answer).contains("내 선호도 기준", "인덕 제육하우스");
        verify(recommendationService).recommendForChatbot(member, "한식", 37.631668, 127.054045);
    }
}
