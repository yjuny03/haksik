package com.example.haksikmokjang.chatbot.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantRecommendationService;
import com.example.haksikmokjang.member.core.domain.Member;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final RestaurantRecommendationService restaurantRecommendationService;

    private AnthropicClient anthropicClient;

    // 🚨 application.properties 나 application.yml 에 ai.claude.api-key=오빠키값 설정해두기!
    @Value("${ai.claude.api-key:${claude.api.key:}}")
    private String claudeApiKey;

    @Value("${ai.claude.model:claude-sonnet-5}")
    private String claudeModel;

    @Value("${ai.claude.timeout-seconds:30}")
    private int claudeTimeoutSeconds;

    @PostConstruct
    void initClient() {
        this.anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(claudeApiKey)
                .timeout(Duration.ofSeconds(claudeTimeoutSeconds > 0 ? claudeTimeoutSeconds : 30))
                .build();
    }

    public String getAiResponse(String userMessage, String preferredFood, Member member) {
        return getAiResponse(userMessage, preferredFood, member, null, null);
    }

    public String getAiResponse(String userMessage, String preferredFood, Member member, Double lat, Double lng) {
        if (isRestaurantRecommendationIntent(userMessage)) {
            return restaurantRecommendationService.recommendForChatbot(
                    member,
                    extractRequestedCategory(userMessage, preferredFood),
                    lat,
                    lng
            );
        }

        // 선호 음식 귓속말 세팅
        String foodInstruction = "";
        if (preferredFood != null && !preferredFood.isEmpty()) {
            foodInstruction = "이 사용자는 현재 [" + preferredFood + "]을(를) 가장 좋아해! 만약 사용자가 음식 추천을 해달라고 하면, 반드시 이 [" + preferredFood + "] 카테고리 안에서 센스 있게 딱 1개의 메뉴만 골라서 추천해 줘!\n";
        } else {
            foodInstruction = "이 사용자는 아직 선호 음식을 설정하지 않았어. 음식 추천을 해달라고 하면, 한식, 중식, 일식, 양식 중에서 랜덤으로 맛있어 보이는 메뉴 1개를 골라 추천해 줘!\n";
        }

        String systemPrompt = "너는 인덕대학교 학생 매칭 앱 '학식목장'의 공식 고객지원 관리자입니다.\n" +
                "사용자의 질문에 대해 감정을 배제하고, 아래 [학식목장 앱 이용 가이드 및 수칙]을 바탕으로 사무적이고 정확한 정보만을 제공해 주세요.\n" +
                "모든 답변은 이모티콘 없이 정중한 존댓말(입니다/해주세요체)을 사용하며, 2줄 이내로 간결하게 요약하여 전달해 주세요.\n\n" +
                "[학식목장 앱 이용 가이드 및 수칙]\n" +
                "1. 학식메이트 매칭: '매칭' 메뉴에서 '학식메이트' 선택 후 지도에서 파트너 탐색이 가능합니다.\n" +
                "2. 소개팅 매칭: '매칭' 메뉴에서 '소개팅' 선택 후 1:1 상대 확인이 가능합니다.\n" +
                "3. 과팅 매칭: '매칭' 메뉴에서 '과팅' 선택 후 그룹 단위 상대 탐색이 가능합니다.\n" +
                "4. 빠른 메뉴: 하단 네비게이션 바([홈, 매칭, 채팅, 커뮤니티, 마이])를 통해 이동이 가능합니다.\n" +
                "5. 채팅 확인: 하단 '채팅' 메뉴에서 대화 내역 확인이 가능합니다.\n" +
                "6. 내 정보 관리: '마이' 탭을 통해 프로필 및 상세 정보 수정이 가능합니다.\n" +
                "7. 오늘의 음식 추천: " + foodInstruction + "\n" +
                "8. 비매너 신고: 커뮤니티 및 채팅방 우측 상단 점 3개 메뉴를 통해 신고 접수가 가능합니다.\n" +
                "9. 오늘의 운세: 관련 질문 시 운세 정보를 제공합니다.\n" +
                "10. 규칙 안내: 항상 공공장소에서 만나고, 매너 있는 대화를 부탁드립니다. 불편한 상황 발생 시 신고 기능을 활용해 주십시오.\n" +
                "11. 제재 사항: 비매너 행위, 광고 및 홍보, 도배, 허위 매칭은 금지되며 적발 시 제재될 수 있습니다.\n" +
                "12. 커뮤니티 주의사항: 비방, 인신공격, 정치/종교 논란 유도, 저작권 침해, 성인물/혐오 표현, 허위사실 유포는 금지됩니다.\n" +
                "13. 매칭 추천: 사용자가 매칭 추천을 요청하면 '학식메이트', '소개팅', '과팅' 중 하나를 임의로 선정하여 추천해주세요.\n" +
                "14. 외부 질문: 가이드 외의 질문은 정보 제공이 어렵다고 답변해 주십시오.\n\n" +
                "주의사항: 가이드에 없는 내용은 임의로 생성하지 말고 '해당 정보는 현재 제공이 어렵습니다'라고 명시해 주세요.";

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(claudeModel)
                    .maxTokens(1024L)
                    .system(systemPrompt)
                    .addUserMessage(userMessage)
                    .build();

            Message response = anthropicClient.messages().create(params);

            // 클로드 응답 까보기 (구조: content[].text)
            String text = response.content().stream()
                    .map(block -> block.text().map(TextBlock::text).orElse(""))
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse(null);

            return text == null
                    ? "현재 시스템 지연으로 응답을 가져오지 못했습니다. 잠시 후 다시 시도해 주십시오."
                    : text;

        } catch (Exception e) {
            log.warn("[CLAUDE-CHATBOT] response failure", e);
            return " 다시 한번 물어봐주세요.";
        }
    }

    private boolean isRestaurantRecommendationIntent(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.replaceAll("\\s+", "");
        if (normalized.contains("매칭추천")) {
            return false;
        }
        boolean asksRecommendation = normalized.contains("추천");
        boolean restaurantContext = normalized.contains("맛집")
                || normalized.contains("음식")
                || normalized.contains("메뉴")
                || normalized.contains("식당")
                || normalized.contains("밥")
                || normalized.contains("점심")
                || normalized.contains("저녁");
        return asksRecommendation && restaurantContext;
    }

    private String extractRequestedCategory(String message, String preferredFood) {
        if (message != null) {
            for (String category : List.of("한식", "중식", "일식", "양식")) {
                if (message.contains(category)) {
                    return category;
                }
            }
        }
        return preferredFood;
    }
}
