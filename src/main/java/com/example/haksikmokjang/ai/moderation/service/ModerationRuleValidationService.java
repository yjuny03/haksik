package com.example.haksikmokjang.ai.moderation.service;

import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ModerationRuleValidationService {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://\\S+|www\\.\\S+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)01[016789][- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)");
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("(?<!\\d)\\d{2,6}[- ]\\d{2,6}(?:[- ]\\d{1,6}){1,3}(?!\\d)");
    private static final Pattern DETAILED_ADDRESS_PATTERN = Pattern.compile("(?:[가-힣]+(?:로|길|동|읍|면|리))\\s*\\d{1,4}(?:-\\d{1,4})?(?:\\s*(?:번지|동|호))?");
    private static final Pattern MASKED_PERSONAL_INFO_PATTERN = Pattern.compile("(?i)\\*{2,}|x{2,}");
    private static final Pattern REPEATED_TOKEN_PATTERN = Pattern.compile("(?i)(?<!\\S)(\\S{2,})(?:\\s+\\1){3,}(?!\\S)");
    private static final Pattern REPEATED_PHRASE_PATTERN = Pattern.compile("(?i)(.{4,30}?)(?:[.!?~\\s]+\\1){2,}");
    private static final Pattern REPEATED_CHARACTER_PATTERN = Pattern.compile("(.)\\1{7,}");
    private static final Pattern REPORTED_TARGET_MARKER = Pattern.compile("(?im)^\\s*reported_target\\s*:");
    private static final Pattern CONTEXT_MARKER = Pattern.compile("(?im)^\\s*context\\s*:");

    private static final List<String> COMMERCIAL_TERMS = List.of(
            "구매", "특가", "오늘만", "할인", "이벤트", "가입", "지급", "클릭", "무료", "수익", "한정",
            "홍보", "광고", "대출", "투자", "텔레그램"
    );
    private static final List<String> HIGH_RISK_SPAM_TERMS = List.of(
            "무조건 수익", "고수익", "수익 보장", "원금 보장", "가입하면", "가입 시", "지급", "무료 코인",
            "텔레그램", "오픈채팅", "선착순", "오늘만", "지금 클릭"
    );
    private static final List<String> ABUSE_TERMS = List.of(
            "바보", "멍청이", "병신", "개병신", "개새끼", "씨발", "미친놈", "미친년", "꺼져", "죽어", "한심하다"
    );
    private static final List<String> SEVERE_ABUSE_TERMS = List.of(
            "병신", "개병신", "개새끼", "씨발", "미친놈", "미친년", "꺼져", "죽어"
    );
    private static final List<String> RELATED_ABUSE_TERMS = List.of(
            "병신", "개병신", "시발련", "시발련아", "씨발련", "씨발련아", "개새끼", "꺼져",
            "처먹어라", "신고나 처먹어라", "ㅇㅈㄹ", "지랄", "한심하다"
    );
    private static final List<String> SEXUAL_HARASSMENT_TERMS = List.of(
            "섹스하자", "자자", "따먹", "박고", "빨아", "벗어", "만져", "보지", "자지", "꼴리",
            "가슴", "엉덩이", "몸매", "성희롱"
    );
    private static final List<String> SEVERE_SEXUAL_HARASSMENT_TERMS = List.of(
            "섹스하자", "따먹", "박고", "빨아", "벗어", "만져", "보지", "자지", "꼴리"
    );
    private static final List<String> RELATED_SEXUAL_HARASSMENT_TERMS = List.of(
            "섹스하자", "자자", "모텔 가자", "모텔가자", "따먹", "박고", "빨아", "벗어봐", "벗어라",
            "만져도 돼", "만져도되", "가슴 만", "엉덩이 만", "몸 보여", "몸보여", "몸 보여줘", "몸보여줘",
            "사진 보내봐", "사진 보내", "야한 사진", "가슴 보여", "엉덩이 보여", "보지", "자지", "꼴리",
            "몸매 죽인다", "몸매 쩐다", "가슴 크다", "엉덩이 크다", "음란한", "색녀"
    );
    private static final List<String> COMMENT_SPAM_TERMS = List.of(
            "오픈채팅", "텔레그램", "리딩방", "가입", "문의", "입금", "출금", "수익", "수익 보장",
            "원금 보장", "무료", "할인", "특가", "이벤트", "홍보", "광고", "구매", "클릭", "카톡", "연락"
    );
    private static final List<String> TARGET_TERMS = List.of(
            "너", "넌", "너는", "너를", "너한테", "너에게", "너희",
            "네가", "니", "니가", "니는", "니를", "니한테",
            "당신", "글쓴이", "작성자", "상대방", "저 사람", "저놈", "저년", "@"
    );
    private static final List<String> CONTEXT_EXCLUSION_TERMS = List.of(
            "라고 말했다", "라고 했", "라고 욕", "욕설 예시", "교육 목적", "신고합니다", "신고했", "피해 내용",
            "피해를 입", "들었습니다", "들었어요", "했습니다", "했어요", "장난", "농담", "자기표현",
            "인용", "표현입니다", "욕을 먹", "예시"
    );
    private static final List<String> SEXUAL_CONTEXT_EXCLUSION_TERMS = List.of(
            "성희롱 예시", "성적 발언 예시", "교육 목적", "신고합니다", "신고했", "피해 내용",
            "피해를 입", "들었습니다", "들었어요", "했습니다", "했어요", "장난", "농담", "자기표현",
            "인용", "표현입니다", "예시"
    );
    private static final List<String> THIRD_PARTY_CONTEXT_TERMS = List.of(
            "이 사람", "저 사람", "그 사람", "남의", "타인"
    );
    private static final List<String> MALICIOUS_DISCLOSURE_TERMS = List.of(
            "신상", "공개한다", "공개합니다", "퍼뜨려", "유포"
    );
    private static final List<String> PERSONAL_INFO_EXCLUSION_TERMS = List.of(
            "제 번호", "내 번호", "제 연락처", "내 연락처", "제 이메일", "내 이메일",
            "문의", "예약", "상담", "영업", "가게", "매장", "사업자", "대표번호", "고객센터"
    );
    private static final List<String> SPAM_CONTEXT_EXCLUSION_TERMS = List.of(
            "광고 신고", "스팸 신고", "신고합니다", "신고했", "예시", "인용", "라고", "정보 공유", "참고 자료",
            "출처", "공식", "관련 자료"
    );
    private static final List<String> RELATED_PERSONAL_INFO_EXCLUSION_TERMS = List.of(
            "제 번호", "내 번호", "제 연락처", "내 연락처", "제 이메일", "내 이메일", "누가 제", "누가 내",
            "개인정보 예시", "예시", "인용", "신고합니다", "신고했", "피해 내용", "피해를 입",
            "가게", "매장", "학교", "기관", "행정실", "대표번호", "고객센터", "사업자", "예약", "문의",
            "공식 연락처", "공식 번호", "정보 제공", "참고"
    );
    private static final List<String> RELATED_PERSONAL_INFO_DISCLOSURE_TERMS = List.of(
            "찾아가라", "찾아가", "박제한다", "박제", "연락해라", "연락해", "전화해라", "전화해",
            "퍼뜨려", "유포", "공개한다", "공개합니다", "신상", "저 사람", "이 사람", "그 사람", "남의", "타인"
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
        boolean sexualHarassment = containsAny(normalized, SEXUAL_HARASSMENT_TERMS);
        boolean target = containsAny(normalized, TARGET_TERMS);
        boolean severeTerm = containsAny(normalized, SEVERE_ABUSE_TERMS);
        boolean excludedContext = containsAny(normalized, CONTEXT_EXCLUSION_TERMS);
        boolean severeAbuse = abuse && severeTerm && target && !excludedContext;
        boolean severeSexualHarassment = sexualHarassment
                && containsAny(normalized, SEVERE_SEXUAL_HARASSMENT_TERMS)
                && target
                && !containsAny(normalized, SEXUAL_CONTEXT_EXCLUSION_TERMS);

        return new RuleEvidence(
                spam,
                spamAutoHide,
                personalInfoMatched,
                personalInfoAutoHideMatched,
                flooding,
                abuse,
                severeAbuse,
                sexualHarassment,
                severeSexualHarassment,
                target,
                excludedContext,
                containsAny(normalized, SEXUAL_CONTEXT_EXCLUSION_TERMS),
                containsAny(normalized, PERSONAL_INFO_EXCLUSION_TERMS),
                List.copyOf(personalInfoTypes)
        );
    }

    public boolean matches(ModerationCategory category, String content) {
        return inspectForCategory(category, content).ruleMatched();
    }

    public CategoryRuleEvidence inspectForCategory(ModerationCategory category, String content) {
        RuleEvidence evidence = inspect(contentForRuleValidation(category, content));
        boolean ruleMatched = switch (category) {
            case SPAM_AD -> evidence.spamAutoHideMatched();
            case PERSONAL_INFO -> evidence.personalInfoAutoHideMatched();
            case FLOODING -> evidence.floodingMatched();
            case ABUSE -> evidence.severeAbuseMatched();
            case SEXUAL_HARASSMENT -> evidence.severeSexualHarassmentMatched();
            default -> false;
        };
        boolean exclusionMatched = switch (category) {
            case ABUSE -> evidence.contextExcluded();
            case SEXUAL_HARASSMENT -> evidence.sexualContextExcluded();
            case PERSONAL_INFO -> evidence.personalInfoContextExcluded();
            default -> false;
        };
        return new CategoryRuleEvidence(ruleMatched, exclusionMatched, evidence.targetMatched());
    }

    public boolean exclusionMatched(ModerationCategory category, String content) {
        return inspectForCategory(category, content).exclusionMatched();
    }

    public boolean targetMatched(ModerationCategory category, String content) {
        return inspectForCategory(category, content).targetMatched();
    }

    public boolean spamContextMatched(String content) {
        RuleEvidence evidence = inspect(content);
        return evidence.spamMatched() || evidence.spamAutoHideMatched();
    }

    public boolean spamRelatedMessageCandidate(String content) {
        String value = content == null ? "" : content;
        String normalized = value.toLowerCase(Locale.ROOT);
        return URL_PATTERN.matcher(value).find()
                || containsAny(normalized, HIGH_RISK_SPAM_TERMS)
                || containsAny(normalized, List.of(
                "오픈채팅", "텔레그램", "수익", "입금", "출금", "원금", "가입", "지급", "리딩방", "투자"
        ));
    }

    public boolean floodingMessageMatched(String content) {
        return inspect(content).floodingMatched();
    }

    public boolean abuseRelatedMessageCandidate(String content) {
        String value = content == null ? "" : content;
        String normalized = value.toLowerCase(Locale.ROOT);
        RuleEvidence evidence = inspect(value);
        return containsAny(normalized, RELATED_ABUSE_TERMS) && !evidence.contextExcluded();
    }

    public boolean abuseRelatedCommentCandidate(String content) {
        String value = content == null ? "" : content;
        RuleEvidence evidence = inspect(value);
        return !evidence.contextExcluded()
                && (evidence.severeAbuseMatched()
                || abuseRelatedMessageCandidate(value));
    }

    public boolean sexualHarassmentRelatedMessageCandidate(String content) {
        String value = content == null ? "" : content;
        String normalized = value.toLowerCase(Locale.ROOT);
        CategoryRuleEvidence evidence = inspectForCategory(ModerationCategory.SEXUAL_HARASSMENT, value);
        if (evidence.exclusionMatched()) {
            return false;
        }
        return evidence.ruleMatched()
                || containsAny(normalized, RELATED_SEXUAL_HARASSMENT_TERMS);
    }

    public boolean sexualHarassmentRelatedCommentCandidate(String content) {
        String value = content == null ? "" : content;
        String normalized = value.toLowerCase(Locale.ROOT);
        CategoryRuleEvidence evidence = inspectForCategory(ModerationCategory.SEXUAL_HARASSMENT, value);
        if (evidence.exclusionMatched()) {
            return false;
        }
        return evidence.ruleMatched()
                || containsAny(normalized, RELATED_SEXUAL_HARASSMENT_TERMS);
    }

    public boolean spamRelatedCommentCandidate(String content) {
        String value = content == null ? "" : content;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, SPAM_CONTEXT_EXCLUSION_TERMS)) {
            return false;
        }
        return containsAny(normalized, HIGH_RISK_SPAM_TERMS)
                || containsAny(normalized, List.of("오픈채팅", "텔레그램", "리딩방", "수익 보장", "원금 보장"))
                || (URL_PATTERN.matcher(value).find() && containsAny(normalized, COMMENT_SPAM_TERMS));
    }

    public boolean personalInfoRelatedMessageCandidate(String content) {
        return personalInfoRelatedCandidate(content);
    }

    public boolean personalInfoRelatedCommentCandidate(String content) {
        return personalInfoRelatedCandidate(content);
    }

    private boolean personalInfoRelatedCandidate(String content) {
        String value = content == null ? "" : content;
        String normalized = value.toLowerCase(Locale.ROOT);
        RuleEvidence evidence = inspect(value);
        if (!evidence.personalInfoMatched()
                || evidence.personalInfoContextExcluded()
                || MASKED_PERSONAL_INFO_PATTERN.matcher(value).find()
                || containsAny(normalized, RELATED_PERSONAL_INFO_EXCLUSION_TERMS)) {
            return false;
        }
        return evidence.personalInfoAutoHideMatched()
                || containsAny(normalized, RELATED_PERSONAL_INFO_DISCLOSURE_TERMS);
    }

    private boolean containsAny(String content, List<String> terms) {
        return terms.stream().anyMatch(content::contains);
    }

    private long countContained(String content, List<String> terms) {
        return terms.stream().filter(content::contains).count();
    }

    private String contentForRuleValidation(ModerationCategory category, String content) {
        return switch (category) {
            case ABUSE, SEXUAL_HARASSMENT -> extractReportedTarget(content);
            default -> content;
        };
    }

    private String extractReportedTarget(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        Matcher reportedTargetMatcher = REPORTED_TARGET_MARKER.matcher(content);
        if (!reportedTargetMatcher.find()) {
            return content;
        }

        int start = reportedTargetMatcher.end();
        Matcher contextMatcher = CONTEXT_MARKER.matcher(content);
        if (contextMatcher.find(start)) {
            String extracted = content.substring(start, contextMatcher.start()).trim();
            return fallbackIfBlank(content, extracted);
        }
        String extracted = content.substring(start).trim();
        return fallbackIfBlank(content, extracted);
    }

    private String fallbackIfBlank(String originalContent, String extractedContent) {
        if (extractedContent != null && !extractedContent.isBlank()) {
            return extractedContent;
        }
        log.warn("[AI-REPORT] reported_target 추출 결과가 비어 있어 전체 content로 서버 규칙 검사를 fallback합니다.");
        return originalContent;
    }

    public record CategoryRuleEvidence(
            boolean ruleMatched,
            boolean exclusionMatched,
            boolean targetMatched
    ) {
    }

    public record RuleEvidence(
            boolean spamMatched,
            boolean spamAutoHideMatched,
            boolean personalInfoMatched,
            boolean personalInfoAutoHideMatched,
            boolean floodingMatched,
            boolean abuseMatched,
            boolean severeAbuseMatched,
            boolean sexualHarassmentMatched,
            boolean severeSexualHarassmentMatched,
            boolean targetMatched,
            boolean contextExcluded,
            boolean sexualContextExcluded,
            boolean personalInfoContextExcluded,
            List<String> personalInfoTypes
    ) {
    }
}
