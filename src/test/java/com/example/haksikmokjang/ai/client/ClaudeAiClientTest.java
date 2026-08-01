package com.example.haksikmokjang.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeAiClientTest {

    private MockWebServer server;
    private ObjectMapper objectMapper;
    private ClaudeAiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper();
        var properties = new AiClaudeProperties("test-key", "claude-test", 1, server.url("/").toString());
        client = new ClaudeAiClient(anthropicClient(properties), properties, new ModerationPolicyService());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private AnthropicClient anthropicClient(AiClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .maxRetries(0)
                .build();
    }

    @Test
    void sendsApiKeyAndJsonContentType() throws Exception {
        server.enqueue(claudeResponse(200, validModerationJson(), "end_turn"));

        client.analyzeModeration(request());

        var sent = server.takeRequest();
        assertThat(sent.getHeader("x-api-key")).isEqualTo("test-key");
        assertThat(sent.getHeader("Content-Type")).contains("application/json");
    }

    @Test
    void parsesStructuredJsonResponse() throws Exception {
        server.enqueue(claudeResponse(200, validModerationJson(), "end_turn"));

        ModerationResult result = client.analyzeModeration(request());

        assertThat(result.category()).isEqualTo(ModerationCategory.SPAM_AD);
        assertThat(result.decision()).isEqualTo(ModerationDecision.AUTO_HIDE);
        assertThat(result.evidence()).containsExactly("상업적 유도 문구", "외부 링크");
        assertThat(result.modelName()).isEqualTo("claude-test");

        JsonNode sent = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(sent.path("model").asText()).isEqualTo("claude-test");
        assertThat(sent.path("system").asText()).contains("학식목장 서비스의 콘텐츠 신고 분류기");

        String userContent = userMessageText(sent);
        assertThat(userContent)
                .contains("<untrusted_content>", "광고 문장")
                .doesNotContain("alternativeCategory", "alternativeReason");
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
        server.enqueue(claudeResponse(200, json, "end_turn"));

        ModerationResult result = client.analyzeModeration(request());

        assertThat(result.alternativeCategory()).isNull();
        assertThat(result.alternativeReason()).isNull();
    }

    @Test
    void missingApiKeyFallsBackToReview() {
        var properties = new AiClaudeProperties("", "claude-test", 1, server.url("/").toString());
        var missingKeyClient = new ClaudeAiClient(anthropicClient(properties), properties, new ModerationPolicyService());

        assertFallback(missingKeyClient.analyzeModeration(request()));
    }

    @Test
    void defaultModelUsesCurrentStableSonnet() {
        var properties = new AiClaudeProperties("test-key", null, 1, server.url("/").toString());

        assertThat(properties.model()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void modelNotFoundFallsBackToReview() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "type", "error",
                "error", Map.of(
                        "type", "not_found_error",
                        "message", "model: claude-test"
                )
        ));
        server.enqueue(new MockResponse().setResponseCode(404)
                .addHeader("Content-Type", "application/json")
                .setBody(body));

        ModerationResult result = client.analyzeModeration(request());

        assertFallback(result);
        assertThat(result.modelName()).isEqualTo("claude-test");
    }

    @Test
    void refusalStopReasonFallsBackToReview() throws Exception {
        server.enqueue(claudeResponse(200, validModerationJson(), "refusal"));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void malformedJsonFallsBackToReview() throws Exception {
        server.enqueue(claudeResponse(200, "{broken", "end_turn"));
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
        server.enqueue(claudeResponse(200, invalid, "end_turn"));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void timeoutFallsBackToReview() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        assertFallback(client.analyzeModeration(request()));
    }

    @Test
    void rateLimitThrowsQuotaExceededException() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("""
                {"type":"error","error":{"type":"rate_limit_error","message":"Quota exceeded"}}
                """));

        assertThatThrownBy(() -> client.analyzeModeration(request()))
                .isInstanceOf(AiProviderQuotaExceededException.class)
                .hasMessage(AiProviderQuotaExceededException.USER_MESSAGE);
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

    private String userMessageText(JsonNode sent) {
        JsonNode content = sent.path("messages").get(0).path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        return content.get(0).path("text").asText();
    }

    private MockResponse claudeResponse(int status, String moderationJson, String stopReason) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "id", "msg_test",
                "type", "message",
                "role", "assistant",
                "model", "claude-test",
                "content", List.of(Map.of("type", "text", "text", moderationJson)),
                "stop_reason", stopReason,
                "stop_sequence", "",
                "usage", Map.of("input_tokens", 10, "output_tokens", 20)
        ));
        return new MockResponse().setResponseCode(status)
                .addHeader("Content-Type", "application/json").setBody(body);
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
}
