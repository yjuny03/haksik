package com.example.haksikmokjang.ai.client;

import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import com.example.haksikmokjang.ai.moderation.dto.ModerationPromptRequest;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import com.example.haksikmokjang.ai.moderation.service.ModerationPolicyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiAiClientTest {

    private MockWebServer server;
    private ObjectMapper objectMapper;
    private GeminiAiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(300));
        factory.setReadTimeout(Duration.ofMillis(300));
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .requestFactory(factory)
                .build();
        var properties = new AiGeminiProperties("test-key", "gemini-test", 1, server.url("/").toString());
        client = new GeminiAiClient(restClient, objectMapper, properties, new ModerationPolicyService());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void sendsJsonContentTypeAndAcceptHeaders() throws Exception {
        server.enqueue(geminiResponse(200, validModerationJson(), "STOP"));

        client.analyzeModeration(request());

        var sent = server.takeRequest();
        assertThat(sent.getHeader("Content-Type")).contains("application/json");
        assertThat(sent.getHeader("Accept")).contains("application/json");
    }

    @Test
    void parsesOctetStreamJsonResponseAsUtf8Bytes() throws Exception {
        server.enqueue(geminiResponse(200, validModerationJson(), "STOP", "application/octet-stream"));

        ModerationResult result = client.analyzeModeration(request());

        assertThat(result.category()).isEqualTo(ModerationCategory.SPAM_AD);
        assertThat(result.decision()).isEqualTo(ModerationDecision.AUTO_HIDE);
        assertThat(result.modelName()).isEqualTo("gemini-test");
    }

    @Test
    void malformedOctetStreamResponseFallsBackToReview() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/octet-stream")
                .setBody("not-json"));

        ModerationResult result = client.analyzeModeration(request());

        assertFallback(result);
        assertThat(result.missingContext()).containsExactly("MALFORMED_JSON");
    }

    @Test
    void parsesStructuredJsonResponse() throws Exception {
        server.enqueue(geminiResponse(200, validModerationJson(), "STOP"));

        ModerationResult result = client.analyzeModeration(request());

        assertThat(result.category()).isEqualTo(ModerationCategory.SPAM_AD);
        assertThat(result.decision()).isEqualTo(ModerationDecision.AUTO_HIDE);
        assertThat(result.evidence()).containsExactly("상업적 유도 문구", "외부 링크");
        assertThat(result.modelName()).isEqualTo("gemini-test");

        JsonNode sent = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(sent.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(sent.path("system_instruction").path("parts").get(0).path("text").asText())
                .contains("학식목장 서비스의 콘텐츠 신고 분류기");
        assertThat(sent.path("contents").get(0).path("parts").get(0).path("text").asText())
                .contains("<untrusted_content>", "광고 문장")
                .doesNotContain("alternativeCategory", "alternativeReason");
    }

    @Test
    void sendsGeminiCompatibleResponseSchema() throws Exception {
        server.enqueue(geminiResponse(200, validModerationJson(), "STOP"));

        client.analyzeModeration(request());

        JsonNode schema = objectMapper.readTree(server.takeRequest().getBody().readUtf8())
                .path("generationConfig")
                .path("responseSchema");
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.has("additionalProperties")).isFalse();
        assertNoAdditionalProperties(schema);
        assertNoArrayType(schema);
        assertThat(schema.path("properties").has("alternativeCategory")).isFalse();
        assertThat(schema.path("properties").has("alternativeReason")).isFalse();
    }

    @Test
    void responseSchemaEnumsDoNotContainEmptyString() throws Exception {
        server.enqueue(geminiResponse(200, validModerationJson(), "STOP"));

        client.analyzeModeration(request());

        JsonNode schema = objectMapper.readTree(server.takeRequest().getBody().readUtf8())
                .path("generationConfig")
                .path("responseSchema");
        assertNoEmptyEnumValue(schema);
    }

    @Test
    void missingAlternativeFieldsAreConvertedToNull() throws Exception {
        String json = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("policyVersion", "MODERATION_POLICY_V1"),
                Map.entry("category", "SPAM_AD"),
                Map.entry("decision", "AUTO_HIDE"),
                Map.entry("riskScore", 0.96),
                Map.entry("confidenceScore", 0.97),
                Map.entry("policyCodes", List.of("SPAM-COMMERCIAL")),
                Map.entry("containsPersonalInfo", false),
                Map.entry("personalInfoTypes", List.of()),
                Map.entry("ruleEvidenceRequired", true),
                Map.entry("reasonSummary", "외부 링크를 이용한 상업적 유도 문구가 확인됩니다."),
                Map.entry("evidence", List.of("상업적 유도 문구", "외부 링크")),
                Map.entry("missingContext", List.of())
        ));
        server.enqueue(geminiResponse(200, json, "STOP"));

        ModerationResult result = client.analyzeModeration(request());

        assertThat(result.alternativeCategory()).isNull();
        assertThat(result.alternativeReason()).isNull();
    }

    @Test
    void missingApiKeyFallsBackToReview() {
        var properties = new AiGeminiProperties("", "gemini-test", 1, server.url("/").toString());
        var missingKeyClient = new GeminiAiClient(
                RestClient.builder().baseUrl(server.url("/").toString()).build(),
                objectMapper,
                properties,
                new ModerationPolicyService()
        );

        assertFallback(missingKeyClient.analyzeModeration(request()));
    }

    @Test
    void defaultModelUsesCurrentStableFlash() {
        var properties = new AiGeminiProperties("test-key", null, 1, server.url("/").toString());

        assertThat(properties.model()).isEqualTo("gemini-3.5-flash");
    }

    @Test
    void modelNotFoundFallsBackToReview() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "error", Map.of(
                        "code", 404,
                        "status", "NOT_FOUND",
                        "message", "This model models/gemini-test is no longer available to new users."
                )
        ));
        server.enqueue(new MockResponse().setResponseCode(404)
                .addHeader("Content-Type", "application/json")
                .setBody(body));

        ModerationResult result = client.analyzeModeration(request());

        assertFallback(result);
        assertThat(result.modelName()).isEqualTo("gemini-test");
    }

    @Test
    void safetyFinishReasonFallsBackToReview() throws Exception {
        server.enqueue(geminiResponse(200, validModerationJson(), "SAFETY"));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void promptSafetyBlockFallsBackToReview() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "promptFeedback", Map.of("blockReason", "SAFETY")
        ));
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json").setBody(body));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void malformedJsonFallsBackToReview() throws Exception {
        server.enqueue(geminiResponse(200, "{broken", "STOP"));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void invalidSemanticJsonFallsBackToReview() throws Exception {
        String invalid = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("policyVersion", "MODERATION_POLICY_V1"),
                Map.entry("category", "SAFE"),
                Map.entry("decision", "AUTO_HIDE"),
                Map.entry("riskScore", 0.97),
                Map.entry("confidenceScore", 0.98),
                Map.entry("policyCodes", List.of()),
                Map.entry("containsPersonalInfo", false),
                Map.entry("personalInfoTypes", List.of()),
                Map.entry("ruleEvidenceRequired", false),
                Map.entry("reasonSummary", "안전하지만 숨김"),
                Map.entry("evidence", List.of()),
                Map.entry("missingContext", List.of()),
                Map.entry("alternativeCategory", "OTHER"),
                Map.entry("alternativeReason", "의미 충돌")
        ));
        server.enqueue(geminiResponse(200, invalid, "STOP"));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void timeoutFallsBackToReview() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void rateLimitFallsBackToReview() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void serverErrorFallsBackToReview() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void emptyResponseFallsBackToReview() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));
        assertFallback(client.analyzeModeration(request()));
    }

    private MockResponse geminiResponse(int status, String moderationJson, String finishReason) throws Exception {
        return geminiResponse(status, moderationJson, finishReason, "application/json");
    }

    private MockResponse geminiResponse(int status, String moderationJson, String finishReason, String contentType) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "finishReason", finishReason,
                        "content", Map.of("parts", List.of(Map.of("text", moderationJson)))
                ))
        ));
        return new MockResponse().setResponseCode(status)
                .addHeader("Content-Type", contentType).setBody(body);
    }

    private String validModerationJson() throws Exception {
        return objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("policyVersion", "MODERATION_POLICY_V1"),
                Map.entry("category", "SPAM_AD"),
                Map.entry("decision", "AUTO_HIDE"),
                Map.entry("riskScore", 0.96),
                Map.entry("confidenceScore", 0.97),
                Map.entry("policyCodes", List.of("SPAM-COMMERCIAL")),
                Map.entry("containsPersonalInfo", false),
                Map.entry("personalInfoTypes", List.of()),
                Map.entry("ruleEvidenceRequired", true),
                Map.entry("reasonSummary", "외부 링크를 이용한 상업적 유도 문구가 확인됩니다."),
                Map.entry("evidence", List.of("상업적 유도 문구", "외부 링크")),
                Map.entry("missingContext", List.of()),
                Map.entry("alternativeCategory", "OTHER"),
                Map.entry("alternativeReason", "반복 여부는 확인되지 않았습니다.")
        ));
    }

    private ModerationPromptRequest request() {
        return new ModerationPromptRequest(1L, "POST", "스팸", "광고 문장 https://example.test");
    }

    private void assertFallback(ModerationResult result) {
        assertThat(result.decision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
        assertThat(result.category()).isEqualTo(ModerationCategory.OTHER);
        assertThat(result.riskScore()).isZero();
        assertThat(result.confidenceScore()).isZero();
        assertThat(result.policyCodes()).containsExactly("AI-PROVIDER-FAILURE");
        assertThat(result.reasonSummary()).isEqualTo("AI 분석을 완료하지 못해 관리자 검토가 필요합니다.");
    }

    private void assertNoAdditionalProperties(JsonNode node) {
        assertThat(node.has("additionalProperties")).isFalse();
        node.forEach(this::assertNoAdditionalProperties);
    }

    private void assertNoArrayType(JsonNode node) {
        if (node.has("type")) {
            assertThat(node.path("type").isArray()).isFalse();
        }
        node.forEach(this::assertNoArrayType);
    }

    private void assertNoEmptyEnumValue(JsonNode node) {
        if (node.has("enum")) {
            assertThat(node.path("enum"))
                    .extracting(JsonNode::asText)
                    .doesNotContain("");
        }
        node.forEach(this::assertNoEmptyEnumValue);
    }
}
