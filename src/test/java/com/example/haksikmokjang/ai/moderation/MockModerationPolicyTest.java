package com.example.haksikmokjang.ai.moderation;

import com.example.haksikmokjang.ai.client.MockAiClient;
import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import com.example.haksikmokjang.ai.moderation.dto.ModerationPromptRequest;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import com.example.haksikmokjang.ai.moderation.service.ModerationPolicyService;
import com.example.haksikmokjang.ai.moderation.service.ModerationRuleValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockModerationPolicyTest {

    private ModerationRuleValidationService ruleService;
    private ModerationPolicyService policyService;
    private MockAiClient mockAiClient;

    @BeforeEach
    void setUp() {
        ruleService = new ModerationRuleValidationService();
        policyService = new ModerationPolicyService();
        mockAiClient = new MockAiClient(ruleService);
    }

    @Test
    void phishingSpamCanBeAutoHidden() {
        String content = "무조건 수익 가입하면 지급 https://spam.example";

        ModerationResult result = analyze(content);

        assertThat(result.category()).isEqualTo(ModerationCategory.SPAM_AD);
        assertThat(policyService.canAutoHide(result, ruleService.matches(result.category(), content))).isTrue();
    }

    @Test
    void singleCommercialPromotionRequiresReview() {
        ModerationResult result = analyze("여름 메뉴 20% 할인 https://shop.example");

        assertThat(result.category()).isEqualTo(ModerationCategory.SPAM_AD);
        assertThat(result.decision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
        assertThat(ruleService.matches(result.category(), "여름 메뉴 20% 할인 https://shop.example")).isFalse();
    }

    @Test
    void ordinarySentenceWithUrlIsSafe() {
        ModerationResult result = analyze("회의 자료는 https://docs.example 에 정리했습니다");

        assertThat(result.category()).isEqualTo(ModerationCategory.SAFE);
        assertThat(result.decision()).isEqualTo(ModerationDecision.NO_ACTION);
    }

    @Test
    void ordinaryPhoneNumberRequiresReviewWithoutDoxxingContext() {
        ModerationResult result = analyze("연락은 010-1234-5678로 주세요");

        assertThat(result.category()).isEqualTo(ModerationCategory.PERSONAL_INFO);
        assertThat(result.decision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
        assertThat(policyService.canAutoHide(result, false)).isFalse();
    }

    @Test
    void thirdPartyPhoneDisclosureCanBeAutoHidden() {
        String content = "이 사람 번호는 010-1234-5678이니 퍼뜨려 주세요";
        ModerationResult result = analyze(content);

        assertThat(result.category()).isEqualTo(ModerationCategory.PERSONAL_INFO);
        assertThat(result.personalInfoTypes()).contains("PHONE");
        assertThat(policyService.canAutoHide(result, ruleService.matches(result.category(), content))).isTrue();
    }

    @Test
    void accountAndDetailedAddressAreDetectedButNeedContextReview() {
        ModerationResult account = analyze("환불 계좌는 123-456-789012입니다");
        ModerationResult address = analyze("배송지는 한빛로 12-3 201호입니다");

        assertThat(account.category()).isEqualTo(ModerationCategory.PERSONAL_INFO);
        assertThat(account.personalInfoTypes()).contains("ACCOUNT");
        assertThat(account.decision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
        assertThat(address.personalInfoTypes()).contains("ADDRESS");
        assertThat(address.decision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
    }

    @Test
    void repeatedContentIsDistinguishedFromSimpleEmphasis() {
        ModerationResult emphasis = analyze("진짜 진짜 진짜 맛있어요");
        String flooding = "광고글 광고글 광고글 광고글";
        ModerationResult repeated = analyze(flooding);

        assertThat(emphasis.category()).isEqualTo(ModerationCategory.SAFE);
        assertThat(repeated.category()).isEqualTo(ModerationCategory.FLOODING);
        assertThat(policyService.canAutoHide(repeated, ruleService.matches(repeated.category(), flooding))).isTrue();
    }

    @Test
    void severeTargetedAbuseCanBeAutoHidden() {
        String content = "너는 정말 개새끼야";

        ModerationResult result = analyze(content);

        assertThat(result.policyCodes()).contains("ABUSE-SEVERE");
        assertThat(result.riskScore()).isGreaterThanOrEqualTo(new BigDecimal("0.95"));
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(new BigDecimal("0.95"));
        assertThat(policyService.canAutoHide(result, ruleService.matches(result.category(), content))).isTrue();
    }

    @Test
    void quotedAbuseRequiresReview() {
        ModerationResult result = analyze("피해 내용으로 '너는 개새끼야'라고 말했다");

        assertThat(result.category()).isEqualTo(ModerationCategory.ABUSE);
        assertThat(result.decision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
        assertThat(result.policyCodes()).doesNotContain("ABUSE-SEVERE");
    }

    @Test
    void generalAbuseRequiresReview() {
        ModerationResult result = analyze("아 진짜 씨발 오늘 왜 이래");

        assertThat(result.category()).isEqualTo(ModerationCategory.ABUSE);
        assertThat(result.decision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
    }

    @Test
    void victimReportOfSevereAbuseRequiresReview() {
        ModerationResult result = analyze("저 사람이 저한테 너는 죽어라고 욕해서 신고합니다");

        assertThat(result.category()).isEqualTo(ModerationCategory.ABUSE);
        assertThat(result.decision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
        assertThat(result.policyCodes()).doesNotContain("ABUSE-SEVERE");
    }

    @Test
    void severeAbuseNeedsBothScoresAtPointNinetyFive() {
        ModerationResult lowConfidence = new ModerationResult(
                MockAiClient.POLICY_VERSION,
                ModerationCategory.ABUSE,
                ModerationDecision.AUTO_HIDE,
                new BigDecimal("0.99"),
                new BigDecimal("0.94"),
                List.of("ABUSE-SEVERE"),
                false,
                List.of(),
                true,
                "특정 대상 고강도 모욕",
                MockAiClient.MODEL_NAME
        );

        assertThat(policyService.canAutoHide(lowConfidence, true)).isFalse();
    }

    @Test
    void safeAutoHideResponseIsInvalid() {
        ModerationResult invalid = new ModerationResult(
                MockAiClient.POLICY_VERSION,
                ModerationCategory.SAFE,
                ModerationDecision.AUTO_HIDE,
                new BigDecimal("0.99"),
                new BigDecimal("0.99"),
                List.of("INVALID"),
                false,
                List.of(),
                true,
                "안전하지만 숨김",
                MockAiClient.MODEL_NAME
        );

        assertThat(policyService.isValid(invalid)).isFalse();
        assertThat(policyService.canAutoHide(invalid, true)).isFalse();
    }

    private ModerationResult analyze(String content) {
        return mockAiClient.analyzeModeration(
                new ModerationPromptRequest(1L, "COMMENT", "신고", content)
        );
    }
}
