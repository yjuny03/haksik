package com.example.haksikmokjang.ai.client;

import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import com.example.haksikmokjang.ai.moderation.ModerationPolicyConstants;
import com.example.haksikmokjang.ai.moderation.dto.ModerationPromptRequest;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import com.example.haksikmokjang.ai.moderation.service.ModerationRuleValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    public static final String MODEL_NAME = "mock-moderation-v1";
    public static final String POLICY_VERSION = ModerationPolicyConstants.POLICY_VERSION;

    private final ModerationRuleValidationService ruleValidationService;

    @Override
    public ModerationResult analyzeModeration(ModerationPromptRequest request) {
        String content = request.content() == null ? "" : request.content();
        var evidence = ruleValidationService.inspect(content);

        if (evidence.personalInfoAutoHideMatched()) {
            return result(
                    ModerationCategory.PERSONAL_INFO,
                    ModerationDecision.AUTO_HIDE,
                    "0.97", "0.98",
                    List.of("PII-DETECTED"),
                    true,
                    evidence.personalInfoTypes(),
                    "개인정보 형식으로 탐지된 값이 공개되어 노출 피해 가능성이 높습니다."
            );
        }

        if (evidence.personalInfoMatched()) {
            return result(
                    ModerationCategory.PERSONAL_INFO,
                    ModerationDecision.REVIEW_REQUIRED,
                    "0.76", "0.90",
                    List.of("PII-DETECTED"),
                    true,
                    evidence.personalInfoTypes(),
                    "개인정보 형식은 탐지되었지만 본인 게시 또는 공개 사업 정보 가능성을 확인해야 합니다."
            );
        }

        if (evidence.spamAutoHideMatched()) {
            return result(
                    ModerationCategory.SPAM_AD,
                    ModerationDecision.AUTO_HIDE,
                    "0.94", "0.96",
                    List.of("SPAM-COMMERCIAL", "SPAM-SUSPICIOUS-URL"),
                    false,
                    List.of(),
                    "외부 링크와 상업적 유도 문구가 함께 확인됩니다."
            );
        }

        if (evidence.spamMatched()) {
            return result(
                    ModerationCategory.SPAM_AD,
                    ModerationDecision.REVIEW_REQUIRED,
                    "0.71", "0.88",
                    List.of("SPAM-COMMERCIAL", "SPAM-SUSPICIOUS-URL"),
                    false,
                    List.of(),
                    "외부 링크와 상업적 홍보 문구가 확인되지만 반복·피싱·수익 유도 여부는 검토가 필요합니다."
            );
        }

        if (evidence.floodingMatched()) {
            return result(
                    ModerationCategory.FLOODING,
                    ModerationDecision.AUTO_HIDE,
                    "0.92", "0.95",
                    List.of("FLOOD-CONTENT-REPEAT"),
                    false,
                    List.of(),
                    "동일 문구 또는 문자가 과도하게 반복되어 서비스 이용을 방해할 가능성이 높습니다."
            );
        }

        if (evidence.severeAbuseMatched()) {
            return result(
                    ModerationCategory.ABUSE,
                    ModerationDecision.AUTO_HIDE,
                    "0.97", "0.97",
                    List.of("ABUSE-DIRECT", "ABUSE-SEVERE"),
                    false,
                    List.of(),
                    "특정 대상을 향한 명백한 고강도 욕설과 심각한 인격 비하가 직접 표현되어 있습니다."
            );
        }

        if (evidence.abuseMatched()) {
            return result(
                    ModerationCategory.ABUSE,
                    ModerationDecision.REVIEW_REQUIRED,
                    "0.72", "0.91",
                    List.of("ABUSE-DIRECT"),
                    false,
                    List.of(),
                    "욕설 또는 모욕 표현이 확인되지만 강화된 자동 처리 조건에는 해당하지 않습니다."
            );
        }

        return result(
                ModerationCategory.SAFE,
                ModerationDecision.NO_ACTION,
                "0.08", "0.92",
                List.of(),
                false,
                List.of(),
                "Mock 정책 규칙에서 명백한 자동 처리 대상이 확인되지 않았습니다."
        );
    }

    private ModerationResult result(
            ModerationCategory category,
            ModerationDecision decision,
            String riskScore,
            String confidenceScore,
            List<String> policyCodes,
            boolean containsPersonalInfo,
            List<String> personalInfoTypes,
            String reasonSummary
    ) {
        // Mock 점수는 실제 AI 신뢰도가 아니라 공모전 데모 흐름을 재현하기 위한 시나리오 값입니다.
        return new ModerationResult(
                POLICY_VERSION,
                category,
                decision,
                new BigDecimal(riskScore),
                new BigDecimal(confidenceScore),
                policyCodes,
                containsPersonalInfo,
                personalInfoTypes,
                true,
                reasonSummary,
                MODEL_NAME
        );
    }
}
