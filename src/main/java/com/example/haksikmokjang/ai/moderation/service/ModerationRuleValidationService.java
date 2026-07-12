package com.example.haksikmokjang.ai.moderation.service;

import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ModerationRuleValidationService {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://\\S+|www\\.\\S+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)01[016789][- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)");
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("(?<!\\d)\\d{2,6}[- ]\\d{2,6}(?:[- ]\\d{1,6}){1,3}(?!\\d)");
    private static final Pattern DETAILED_ADDRESS_PATTERN = Pattern.compile("(?:[가-힣]+(?:로|길|동|읍|면|리))\\s*\\d{1,4}(?:-\\d{1,4})?(?:\\s*(?:번지|동|호))?");
    private static final Pattern REPEATED_TOKEN_PATTERN = Pattern.compile("(?i)(?<!\\S)(\\S{2,})(?:\\s+\\1){3,}(?!\\S)");
    private static final Pattern REPEATED_PHRASE_PATTERN = Pattern.compile("(?i)(.{4,30}?)(?:[.!?~\\s]+\\1){2,}");
    private static final Pattern REPEATED_CHARACTER_PATTERN = Pattern.compile("(.)\\1{7,}");

    private static final List<String> COMMERCIAL_TERMS = List.of(
            "구매", "특가", "오늘만", "할인", "이벤트", "가입", "지급", "클릭", "무료", "수익", "한정",
            "홍보", "광고", "대출", "투자", "텔레그램"
    );
    private static final List<String> HIGH_RISK_SPAM_TERMS = List.of(
            "무조건 수익", "고수익", "수익 보장", "원금 보장", "가입하면", "가입 시", "지급", "무료 코인",
            "텔레그램", "오픈채팅", "선착순", "오늘만", "지금 클릭"
    );
    private static final List<String> ABUSE_TERMS = List.of(
            "바보", "멍청이", "병신", "개새끼", "씨발", "죽어"
    );
    private static final List<String> SEVERE_ABUSE_TERMS = List.of(
            "병신", "개새끼", "씨발", "죽어"
    );
    private static final List<String> TARGET_TERMS = List.of(
            "너", "네가", "니가", "당신", "저 사람", "저놈", "저년", "@"
    );
    private static final List<String> CONTEXT_EXCLUSION_TERMS = List.of(
            "라고 말했다", "라고 했", "라고 욕", "욕설 예시", "교육 목적", "신고합니다", "신고했", "피해 내용",
            "피해를 입", "들었습니다", "장난", "인용", "표현입니다", "욕을 먹"
    );
    private static final List<String> THIRD_PARTY_CONTEXT_TERMS = List.of(
            "이 사람", "저 사람", "그 사람", "남의", "타인"
    );
    private static final List<String> MALICIOUS_DISCLOSURE_TERMS = List.of(
            "신상", "공개한다", "공개합니다", "퍼뜨려", "유포"
    );

    public RuleEvidence inspect(String rawContent) {
        String content = rawContent == null ? "" : rawContent;
        String normalized = content.toLowerCase(Locale.ROOT);

        List<String> personalInfoTypes = new ArrayList<>();
        if (NATIONAL_ID_PATTERN.matcher(content).find()) personalInfoTypes.add("NATIONAL_ID");
        if (PHONE_PATTERN.matcher(content).find()) personalInfoTypes.add("PHONE");
        if (EMAIL_PATTERN.matcher(content).find()) personalInfoTypes.add("EMAIL");
        if (ACCOUNT_PATTERN.matcher(content).find()) personalInfoTypes.add("ACCOUNT");
        if (DETAILED_ADDRESS_PATTERN.matcher(content).find()) personalInfoTypes.add("ADDRESS");

        boolean personalInfoMatched = !personalInfoTypes.isEmpty();
        boolean personalInfoAutoHideMatched = personalInfoTypes.contains("NATIONAL_ID")
                || (personalInfoMatched && (containsAny(normalized, THIRD_PARTY_CONTEXT_TERMS)
                || containsAny(normalized, MALICIOUS_DISCLOSURE_TERMS)));
        boolean hasUrl = URL_PATTERN.matcher(content).find();
        boolean spam = hasUrl && containsAny(normalized, COMMERCIAL_TERMS);
        long commercialClueCount = countContained(normalized, COMMERCIAL_TERMS);
        boolean spamAutoHide = spam && (containsAny(normalized, HIGH_RISK_SPAM_TERMS) || commercialClueCount >= 3);
        boolean flooding = REPEATED_TOKEN_PATTERN.matcher(content).find()
                || REPEATED_PHRASE_PATTERN.matcher(content).find()
                || REPEATED_CHARACTER_PATTERN.matcher(content).find();
        boolean abuse = containsAny(normalized, ABUSE_TERMS);
        boolean target = containsAny(normalized, TARGET_TERMS);
        boolean severeTerm = containsAny(normalized, SEVERE_ABUSE_TERMS);
        boolean excludedContext = containsAny(normalized, CONTEXT_EXCLUSION_TERMS);
        boolean severeAbuse = abuse && severeTerm && target && !excludedContext;

        return new RuleEvidence(
                spam,
                spamAutoHide,
                personalInfoMatched,
                personalInfoAutoHideMatched,
                flooding,
                abuse,
                severeAbuse,
                target,
                excludedContext,
                List.copyOf(personalInfoTypes)
        );
    }

    public boolean matches(ModerationCategory category, String content) {
        RuleEvidence evidence = inspect(content);
        return switch (category) {
            case SPAM_AD -> evidence.spamAutoHideMatched();
            case PERSONAL_INFO -> evidence.personalInfoAutoHideMatched();
            case FLOODING -> evidence.floodingMatched();
            case ABUSE -> evidence.severeAbuseMatched();
            default -> false;
        };
    }

    private boolean containsAny(String content, List<String> terms) {
        return terms.stream().anyMatch(content::contains);
    }

    private long countContained(String content, List<String> terms) {
        return terms.stream().filter(content::contains).count();
    }

    public record RuleEvidence(
            boolean spamMatched,
            boolean spamAutoHideMatched,
            boolean personalInfoMatched,
            boolean personalInfoAutoHideMatched,
            boolean floodingMatched,
            boolean abuseMatched,
            boolean severeAbuseMatched,
            boolean targetMatched,
            boolean contextExcluded,
            List<String> personalInfoTypes
    ) {
    }
}
