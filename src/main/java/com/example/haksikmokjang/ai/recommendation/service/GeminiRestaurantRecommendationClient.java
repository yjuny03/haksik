package com.example.haksikmokjang.ai.recommendation.service;

import com.example.haksikmokjang.ai.client.AiGeminiProperties;
import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class GeminiRestaurantRecommendationClient {

    private static final String SYSTEM_PROMPT = """
            당신은 학식목장 서비스의 식당 추천 설명 생성기다.
            서버가 제공한 candidates 안의 storeId와 menuId만 사용하라.
            없는 가게, 없는 메뉴, 없는 가격, 없는 평점, 없는 거리, 없는 리뷰를 만들지 마라.
            가격, 평점, 거리, 리뷰 수 같은 숫자는 설명에서 새로 쓰지 말고, 후보 안의 사실만 참고하라.
            JSON 객체 하나만 응답하라.
            recommendations 배열의 각 항목은 recommendedStoreId, recommendedMenuId, rank, reason,
            matchedPreference, distanceText, reviewSummary, cautionMessage 필드를 포함한다.
            """;

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;
    private final AiGeminiProperties properties;
    private final String recommendationModel;

    public GeminiRestaurantRecommendationClient(
            ObjectMapper objectMapper,
            AiGeminiProperties properties,
            @Value("${ai.gemini.recommendation-model:${ai.gemini.model:gemini-3.5-flash}}") String recommendationModel
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.recommendationModel = recommendationModel;
        this.geminiRestClient = createRestClient(properties);
    }

    private RestClient createRestClient(AiGeminiProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<GeminiRecommendationResult> recommend(
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

        GeminiHttpResponse response = null;
        try {
            response = geminiRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", properties.apiKey())
                            .build(recommendationModel))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(buildRequest(candidates, preferredFoodCategory, requestedCategory,
                            chatPreferenceCounts, majorityPreferredCategory, limit))
                    .exchange((request, clientResponse) -> new GeminiHttpResponse(
                            clientResponse.getStatusCode(),
                            clientResponse.getHeaders().getContentType(),
                            new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8)
                    ));

            if (!response.statusCode().is2xxSuccessful()) {
                log.warn("[GEMINI-RESTAURANT] request failed status={} contentType={} model={} body={}",
                        response.statusCode(),
                        response.contentType(),
                        recommendationModel,
                        snippet(response.body()));
                return Optional.empty();
            }

            return Optional.of(parseResponse(response.body()));
        } catch (Exception exception) {
            log.warn("[GEMINI-RESTAURANT] fallback reason={} status={} contentType={} model={} body={}",
                    exception.getClass().getSimpleName(),
                    response == null ? "N/A" : response.statusCode(),
                    response == null ? "N/A" : response.contentType(),
                    recommendationModel,
                    response == null ? "" : snippet(response.body()));
            return Optional.empty();
        }
    }

    private Map<String, Object> buildRequest(
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

        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_PROMPT))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", toJson(userPayload)))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );
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

    private GeminiRecommendationResult parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            String text = extractText(candidates.get(0));
            return objectMapper.readValue(stripCodeFence(text), GeminiRecommendationResult.class);
        }
        return objectMapper.readValue(stripCodeFence(responseBody), GeminiRecommendationResult.class);
    }

    private String extractText(JsonNode candidate) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                builder.append(text);
            }
        }
        return builder.toString();
    }

    private String stripCodeFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutStart = trimmed.replaceFirst("^```(?:json)?\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize Gemini recommendation payload", exception);
        }
    }

    private String snippetForPrompt(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String snippet(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 800 ? normalized : normalized.substring(0, 800) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record GeminiHttpResponse(
            HttpStatusCode statusCode,
            MediaType contentType,
            String body
    ) {
    }
}
