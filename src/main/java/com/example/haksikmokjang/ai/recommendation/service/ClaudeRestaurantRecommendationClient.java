package com.example.haksikmokjang.ai.recommendation.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.example.haksikmokjang.ai.client.AiClaudeProperties;
import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class ClaudeRestaurantRecommendationClient {

    private static final String SYSTEM_PROMPT = """
            당신은 학식목장 서비스의 식당 추천 설명 생성기다.
            서버가 제공한 candidates 안의 storeId와 menuId만 사용하라.
            없는 가게, 없는 메뉴, 없는 가격, 없는 평점, 없는 거리, 없는 리뷰를 만들지 마라.
            가격, 평점, 거리, 리뷰 수 같은 숫자는 설명에서 새로 쓰지 말고, 후보 안의 사실만 참고하라.
            JSON 객체 하나만 응답하라.
            recommendations 배열의 각 항목은 recommendedStoreId, recommendedMenuId, rank, reason,
            matchedPreference, distanceText, reviewSummary, cautionMessage 필드를 포함한다.
            """;

    private static final long MAX_OUTPUT_TOKENS = 4096L;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;
    private final AiClaudeProperties properties;
    private final String recommendationModel;

    public ClaudeRestaurantRecommendationClient(
            ObjectMapper objectMapper,
            AiClaudeProperties properties,
            @Value("${ai.claude.recommendation-model:${ai.claude.model:claude-sonnet-5}}") String recommendationModel
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.recommendationModel = recommendationModel;
        this.anthropicClient = createClient(properties);
    }

    private AnthropicClient createClient(AiClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                // 쿼터 초과 시 SDK 자동 재시도로 요청을 추가 소모하지 않도록 재시도를 끈다.
                .maxRetries(0)
                .build();
    }

    public Optional<ClaudeRecommendationResult> recommend(
            List<RestaurantCandidate> candidates,
            String preferredFoodCategory,
            String requestedCategory,
            Map<String, Long> chatPreferenceCounts,
            String majorityPreferredCategory,
            int limit
    ) {
        if (properties.apiKey() == null || properties.apiKey().isBlank() || candidates.isEmpty()) {
            return Optional.empty();
        }

        try {
            StructuredMessageCreateParams<ClaudeRecommendationResult> params = MessageCreateParams.builder()
                    .model(recommendationModel)
                    .maxTokens(MAX_OUTPUT_TOKENS)
                    .system(SYSTEM_PROMPT)
                    .outputConfig(ClaudeRecommendationResult.class)
                    .addUserMessage(toJson(buildUserPayload(candidates, preferredFoodCategory, requestedCategory,
                            chatPreferenceCounts, majorityPreferredCategory, limit)))
                    .build();

            StructuredMessage<ClaudeRecommendationResult> response = anthropicClient.messages().create(params);

            ClaudeRecommendationResult result = extractParsed(response);
            if (result == null) {
                log.warn("[CLAUDE-RESTAURANT] empty structured response model={}", recommendationModel);
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception exception) {
            log.warn("[CLAUDE-RESTAURANT] fallback reason={} model={}",
                    exception.getClass().getSimpleName(),
                    recommendationModel);
            return Optional.empty();
        }
    }

    private ClaudeRecommendationResult extractParsed(StructuredMessage<ClaudeRecommendationResult> response) {
        return response.content().stream()
                .map(StructuredContentBlock::text)
                .flatMap(Optional::stream)
                .findFirst()
                .map(StructuredTextBlock::text)
                .orElse(null);
    }

    private Map<String, Object> buildUserPayload(
            List<RestaurantCandidate> candidates,
            String preferredFoodCategory,
            String requestedCategory,
            Map<String, Long> chatPreferenceCounts,
            String majorityPreferredCategory,
            int limit
    ) {
        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("policy", Map.of(
                "mustUseOnlyCandidateIds", true,
                "doNotInventStoreMenuPriceReview", true,
                "useDbValuesOnly", true
        ));
        userPayload.put("userContext", Map.of(
                "preferredFoodCategory", safe(preferredFoodCategory),
                "requestedCategory", safe(requestedCategory),
                "chatPreferredFoodCategoryCounts", chatPreferenceCounts == null ? Map.of() : chatPreferenceCounts,
                "majorityPreferredFoodCategory", safe(majorityPreferredCategory),
                "limit", limit
        ));
        userPayload.put("candidates", candidates.stream().map(this::candidatePayload).toList());
        return userPayload;
    }

    private Map<String, Object> candidatePayload(RestaurantCandidate candidate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("storeId", candidate.storeId());
        payload.put("storeName", candidate.storeName());
        payload.put("category", candidate.getStore().getCategory());
        payload.put("distanceMeters", candidate.getDistanceMeters());
        payload.put("averageRating", candidate.averageRating());
        payload.put("reviewCount", candidate.reviewCount());
        payload.put("dbScore", candidate.getScore() == null ? 0 : candidate.getScore().total());
        payload.put("menus", candidate.getMenus().stream().limit(5).map(this::menuPayload).toList());
        payload.put("reviewSnippets", candidate.getReviews().stream()
                .limit(3)
                .map(StoreReview::getContent)
                .filter(content -> content != null && !content.isBlank())
                .map(this::snippetForPrompt)
                .toList());
        return payload;
    }

    private Map<String, Object> menuPayload(Menu menu) {
        return Map.of(
                "menuId", menu.getMenuId(),
                "name", safe(menu.getName())
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize Claude recommendation payload", exception);
        }
    }

    private String snippetForPrompt(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
