package com.example.haksikmokjang.ai.recommendation.service;

import com.example.haksikmokjang.ai.recommendation.dto.LocationResponse;
import com.example.haksikmokjang.ai.recommendation.dto.RecommendedMenuResponse;
import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationItem;
import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationRequest;
import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationResponse;
import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationSource;
import com.example.haksikmokjang.chat.chatroom.domain.ChatRoom;
import com.example.haksikmokjang.chat.chatroom.domain.ChatRoomMember;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomMemberRepository;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomRepository;
import com.example.haksikmokjang.fileattachment.domain.FileAttachment;
import com.example.haksikmokjang.fileattachment.repository.FileAttachmentRepository;
import com.example.haksikmokjang.global.exception.CustomException;
import com.example.haksikmokjang.global.exception.ErrorCode;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.MemberLocation;
import com.example.haksikmokjang.member.core.repository.MemberLocationRepository;
import com.example.haksikmokjang.member.signup.user.domain.UserProfile;
import com.example.haksikmokjang.member.signup.user.repository.UserProfileRepository;
import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantRecommendationService {

    private static final double DEFAULT_RADIUS_KM = 1.0;
    private static final double MAX_RADIUS_KM = 3.0;
    private static final int MIN_CANDIDATES_BEFORE_EXPAND = 3;
    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 5;

    private final RestaurantCandidateQueryService candidateQueryService;
    private final RestaurantScoringService scoringService;
    private final ClaudeRestaurantRecommendationClient claudeClient;
    private final UserProfileRepository userProfileRepository;
    private final MemberLocationRepository memberLocationRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public RestaurantRecommendationResponse recommend(Member member, RestaurantRecommendationRequest request) {
        if (member == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        UserProfile profile = userProfileRepository.findByMember(member)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_PROFILE_NOT_FOUND));

        RestaurantLocation center = resolveLocation(profile, request);
        String preferredCategory = blankToNull(profile.getPreferredFoodCategory());
        String requestedCategory = blankToNull(request.getCategory());
        int limit = normalizeLimit(request.getLimit());

        return recommendInternal(request, center, preferredCategory, requestedCategory, limit, Map.of(), null);
    }

    public RestaurantRecommendationResponse recommendForChatRoom(
            Long chatRoomId,
            Member member,
            RestaurantRecommendationRequest request
    ) {
        if (member == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }

        UserProfile profile = userProfileRepository.findByMember(member)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_PROFILE_NOT_FOUND));
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoomMemberRepository.existsByChatRoomAndMember(chatRoom, member)) {
            throw new CustomException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
        }

        RestaurantLocation center = resolveLocation(profile, request);
        Map<String, Long> preferenceCounts = collectChatPreferenceCounts(chatRoom);
        String majorityCategory = resolveMajorityCategory(preferenceCounts, center, initialRadius(request));
        String preferredCategory = blankToNull(profile.getPreferredFoodCategory());
        String requestedCategory = blankToNull(request.getCategory());
        int limit = normalizeLimit(request.getLimit());

        return recommendInternal(
                request,
                center,
                preferredCategory,
                requestedCategory,
                limit,
                preferenceCounts,
                majorityCategory
        );
    }

    private RestaurantRecommendationResponse recommendInternal(
            RestaurantRecommendationRequest request,
            RestaurantLocation center,
            String preferredCategory,
            String requestedCategory,
            int limit,
            Map<String, Long> chatPreferenceCounts,
            String majorityCategory
    ) {
        CandidateSearchResult searchResult = findExpandedCandidates(center, initialRadius(request));
        List<RestaurantCandidate> scoredCandidates = searchResult.candidates().stream()
                .map(candidate -> scoreCandidate(candidate, preferredCategory, requestedCategory, chatPreferenceCounts, majorityCategory))
                .sorted(Comparator.comparing((RestaurantCandidate candidate) -> candidate.getScore().total()).reversed()
                        .thenComparingInt(RestaurantCandidate::getDistanceMeters))
                .toList();

        if (scoredCandidates.isEmpty()) {
            return baseResponse(center, preferredCategory, initialRadius(request), searchResult.radiusKm(), List.of(),
                    RestaurantRecommendationSource.DB_FALLBACK, "NO_CANDIDATES", chatPreferenceCounts, majorityCategory);
        }

        List<RestaurantCandidate> claudeCandidates = scoredCandidates.stream().limit(MAX_LIMIT).toList();
        Optional<ClaudeRecommendationResult> claudeResult = claudeClient.recommend(
                claudeCandidates,
                preferredCategory,
                requestedCategory,
                chatPreferenceCounts,
                majorityCategory,
                limit
        );

        if (claudeResult.isPresent()) {
            List<RestaurantRecommendationItem> validatedItems = toClaudeItems(claudeResult.get(), claudeCandidates, limit);
            if (!validatedItems.isEmpty()) {
                return baseResponse(center, preferredCategory, initialRadius(request), searchResult.radiusKm(), validatedItems,
                        RestaurantRecommendationSource.CLAUDE, null, chatPreferenceCounts, majorityCategory);
            }
        }

        return baseResponse(
                center,
                preferredCategory,
                initialRadius(request),
                searchResult.radiusKm(),
                toDbFallbackItems(scoredCandidates, limit, fallbackReason(chatPreferenceCounts)),
                RestaurantRecommendationSource.DB_FALLBACK,
                "CLAUDE_UNAVAILABLE",
                chatPreferenceCounts,
                majorityCategory
        );
    }

    public String recommendForChatbot(Member member, String requestedCategory) {
        return recommendForChatbot(member, requestedCategory, null, null);
    }

    public String recommendForChatbot(Member member, String requestedCategory, Double lat, Double lng) {
        if (member == null) {
            return "맛집 추천을 위해 로그인이 필요합니다.";
        }
        try {
            RestaurantRecommendationResponse response = recommend(member, RestaurantRecommendationRequest.builder()
                    .lat(lat)
                    .lng(lng)
                    .category(requestedCategory)
                    .limit(1)
                    .context("PERSONAL_RECOMMENDATION")
                    .build());

            if (response.getRecommendations() == null || response.getRecommendations().isEmpty()) {
                return "내 선호도 기준으로 주변 가게를 찾았지만 추천 가능한 가게가 부족합니다. 위치를 갱신하거나 반경을 넓혀보세요.";
            }

            RestaurantRecommendationItem item = response.getRecommendations().get(0);
            String menuText = item.getRecommendedMenu() == null
                    ? "판매 중 메뉴"
                    : item.getRecommendedMenu().getName();
            String categoryText = response.getPreferredFoodCategory() == null
                    ? "내 선호도"
                    : response.getPreferredFoodCategory() + " 선호도";
            String reason = item.getReason() == null || item.getReason().isBlank()
                    ? "가까운 거리와 평점, 리뷰, 판매 중 메뉴를 기준으로 추천했어요."
                    : item.getReason();

            return String.format(
                    "오늘은 내 선호도 기준으로 %s와 가까운 거리를 반영해 %s을(를) 추천해요. 추천 메뉴는 %s이고, %s",
                    categoryText,
                    item.getStoreName(),
                    menuText,
                    reason
            );
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.LOCATION_NOT_FOUND) {
                return "내 선호도 기준 추천을 위해 현재 위치가 필요합니다. 위치 권한을 허용하거나 지도를 먼저 열어 위치를 갱신해주세요.";
            }
            if (exception.getErrorCode() == ErrorCode.USER_PROFILE_NOT_FOUND) {
                return "내 선호도 기준 추천을 위해 사용자 프로필 정보가 필요합니다.";
            }
            if (exception.getErrorCode() == ErrorCode.MEMBER_NOT_FOUND) {
                return "맛집 추천을 위해 로그인이 필요합니다.";
            }
            return "내 선호도 기준 추천 정보를 가져오지 못했습니다. 잠시 후 다시 시도해주세요.";
        }
    }

    private CandidateSearchResult findExpandedCandidates(RestaurantLocation center, double initialRadiusKm) {
        double[] radii = new double[]{initialRadiusKm, 2.0, MAX_RADIUS_KM};
        List<RestaurantCandidate> candidates = List.of();
        double usedRadius = initialRadiusKm;

        for (double radius : radii) {
            if (radius < initialRadiusKm || radius > MAX_RADIUS_KM) {
                continue;
            }
            usedRadius = radius;
            candidates = candidateQueryService.findCandidates(center, radius);
            if (candidates.size() >= MIN_CANDIDATES_BEFORE_EXPAND || radius >= MAX_RADIUS_KM) {
                break;
            }
        }

        return new CandidateSearchResult(candidates, usedRadius);
    }

    private RestaurantLocation resolveLocation(UserProfile profile, RestaurantRecommendationRequest request) {
        if (request.getLat() != null && request.getLng() != null) {
            validateCoordinate(request.getLat(), request.getLng());
            return new RestaurantLocation(request.getLat(), request.getLng());
        }

        MemberLocation location = memberLocationRepository.findByUserProfile(profile)
                .filter(memberLocation -> "Y".equals(memberLocation.getLocationPublicYn()))
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));

        double lat = location.getLatitude().doubleValue();
        double lng = location.getLongitude().doubleValue();
        validateCoordinate(lat, lng);
        return new RestaurantLocation(lat, lng);
    }

    private RestaurantCandidate scoreCandidate(
            RestaurantCandidate candidate,
            String preferredCategory,
            String requestedCategory,
            Map<String, Long> chatPreferenceCounts,
            String majorityCategory
    ) {
        Menu recommendedMenu = selectMenuForPreferences(candidate, chatPreferenceCounts, majorityCategory);
        RestaurantScore score = scoringService.score(
                candidate,
                preferredCategory,
                requestedCategory,
                chatPreferenceCounts,
                majorityCategory,
                recommendedMenu
        );
        return candidate.withScore(score).withRecommendedMenu(recommendedMenu);
    }

    private Map<String, Long> collectChatPreferenceCounts(ChatRoom chatRoom) {
        Map<String, Long> counts = chatRoomMemberRepository.findAllByChatRoom(chatRoom).stream()
                .map(ChatRoomMember::getMember)
                .map(userProfileRepository::findByMember)
                .flatMap(Optional::stream)
                .map(UserProfile::getPreferredFoodCategory)
                .map(this::blankToNull)
                .filter(category -> category != null)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String resolveMajorityCategory(Map<String, Long> preferenceCounts, RestaurantLocation center, double radiusKm) {
        if (preferenceCounts == null || preferenceCounts.isEmpty()) {
            return null;
        }

        long maxCount = preferenceCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<String> winners = preferenceCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .toList();

        if (winners.size() == 1) {
            return winners.get(0);
        }

        Map<String, Long> candidateCounts = candidateQueryService.findCandidates(center, radiusKm).stream()
                .map(candidate -> blankToNull(candidate.getStore().getCategory()))
                .filter(category -> category != null)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        long bestCandidateCount = winners.stream()
                .mapToLong(category -> candidateCounts.getOrDefault(category, 0L))
                .max()
                .orElse(0);

        List<String> candidateCountWinners = winners.stream()
                .filter(category -> candidateCounts.getOrDefault(category, 0L) == bestCandidateCount)
                .toList();

        if (candidateCountWinners.isEmpty()) {
            return winners.get(ThreadLocalRandom.current().nextInt(winners.size()));
        }
        return candidateCountWinners.get(ThreadLocalRandom.current().nextInt(candidateCountWinners.size()));
    }

    private Menu selectMenuForPreferences(
            RestaurantCandidate candidate,
            Map<String, Long> preferenceCounts,
            String majorityCategory
    ) {
        List<String> categories = preferredCategoriesInPriority(preferenceCounts, majorityCategory);
        for (String category : categories) {
            List<Menu> matchingMenus = candidate.getMenus().stream()
                    .filter(menu -> menuMatchesCategory(menu, category))
                    .toList();
            if (!matchingMenus.isEmpty()) {
                return matchingMenus.get(ThreadLocalRandom.current().nextInt(matchingMenus.size()));
            }
        }
        return candidate.defaultMenu();
    }

    private List<String> preferredCategoriesInPriority(Map<String, Long> preferenceCounts, String majorityCategory) {
        if (preferenceCounts == null || preferenceCounts.isEmpty()) {
            return majorityCategory == null ? List.of() : List.of(majorityCategory);
        }

        List<String> categories = preferenceCounts.keySet().stream()
                .filter(category -> category != null && !category.isBlank())
                .toList();

        if (majorityCategory == null || categories.contains(majorityCategory)) {
            return categories;
        }

        return java.util.stream.Stream.concat(java.util.stream.Stream.of(majorityCategory), categories.stream())
                .toList();
    }

    private boolean menuMatchesCategory(Menu menu, String category) {
        if (menu == null || menu.getName() == null || category == null) {
            return false;
        }
        Map<String, List<String>> keywords = Map.of(
                "한식", List.of("김치", "찌개", "덮밥", "국밥", "비빔", "제육"),
                "중식", List.of("짜장", "짬뽕", "탕수", "마라"),
                "일식", List.of("돈카츠", "초밥", "라멘", "우동", "덮밥"),
                "양식", List.of("파스타", "피자", "스테이크", "버거", "샐러드")
        );
        return keywords.getOrDefault(category, List.of()).stream().anyMatch(menu.getName()::contains);
    }

    private void validateCoordinate(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new CustomException(ErrorCode.INVALID_LOCATION_VALUE);
        }
    }

    private List<RestaurantRecommendationItem> toClaudeItems(
            ClaudeRecommendationResult result,
            List<RestaurantCandidate> candidates,
            int limit
    ) {
        Map<Long, RestaurantCandidate> candidateByStoreId = candidates.stream()
                .collect(Collectors.toMap(RestaurantCandidate::storeId, Function.identity()));

        if (result.recommendations() == null || result.recommendations().isEmpty()) {
            return List.of();
        }

        return result.recommendations().stream()
                .sorted(Comparator.comparing(item -> item.rank() == null ? Integer.MAX_VALUE : item.rank()))
                .map(item -> toValidatedClaudeItem(item, candidateByStoreId))
                .flatMap(Optional::stream)
                .limit(limit)
                .toList();
    }

    private Optional<RestaurantRecommendationItem> toValidatedClaudeItem(
            ClaudeRecommendationResult.Item item,
            Map<Long, RestaurantCandidate> candidateByStoreId
    ) {
        RestaurantCandidate candidate = candidateByStoreId.get(item.recommendedStoreId());
        if (candidate == null) {
            return Optional.empty();
        }

        Menu menu = candidate.menuById(item.recommendedMenuId());
        if (menu == null) {
            return Optional.empty();
        }

        return Optional.of(toItem(
                candidate,
                menu,
                item.rank(),
                safeText(item.reason(), defaultReason(candidate)),
                safeText(item.matchedPreference(), defaultMatchedPreference(candidate)),
                safeText(item.distanceText(), defaultDistanceText(candidate)),
                safeText(item.reviewSummary(), defaultReviewSummary(candidate)),
                safeText(item.cautionMessage(), "")
        ));
    }

    private List<RestaurantRecommendationItem> toDbFallbackItems(
            List<RestaurantCandidate> candidates,
            int limit,
            String fallbackReason
    ) {
        return candidates.stream()
                .limit(limit)
                .map(candidate -> toItem(
                        candidate,
                        candidate.recommendedMenuOrDefault(),
                        candidates.indexOf(candidate) + 1,
                        fallbackReason,
                        defaultMatchedPreference(candidate),
                        defaultDistanceText(candidate),
                        defaultReviewSummary(candidate),
                        defaultCaution(candidate)
                ))
                .toList();
    }

    private RestaurantRecommendationItem toItem(
            RestaurantCandidate candidate,
            Menu menu,
            Integer rank,
            String reason,
            String matchedPreference,
            String distanceText,
            String reviewSummary,
            String cautionMessage
    ) {
        return RestaurantRecommendationItem.builder()
                .rank(rank)
                .storeId(candidate.storeId())
                .storeName(candidate.storeName())
                .address(candidate.getStore().getAddress())
                .latitude(candidate.getStore().getLatitude())
                .longitude(candidate.getStore().getLongitude())
                .category(candidate.getStore().getCategory())
                .businessStatus(candidate.getStore().getBusinessStatus() == null
                        ? null
                        : candidate.getStore().getBusinessStatus().name())
                .distanceMeters(candidate.getDistanceMeters())
                .distanceText(distanceText)
                .averageRating(round(candidate.averageRating()))
                .reviewCount(candidate.reviewCount())
                .recommendedMenu(menu == null ? null : RecommendedMenuResponse.builder()
                        .menuId(menu.getMenuId())
                        .name(menu.getName())
                        .price(menu.getPrice())
                        .imageId(firstImageId("MENU", menu.getMenuId()))
                        .build())
                .storeImageId(candidate.getStoreImageId())
                .reason(reason)
                .matchedPreference(matchedPreference)
                .reviewSummary(reviewSummary)
                .cautionMessage(cautionMessage)
                .dbScore(candidate.getScore() == null ? 0 : candidate.getScore().total())
                .build();
    }

    private RestaurantRecommendationResponse baseResponse(
            RestaurantLocation center,
            String preferredCategory,
            double radiusKm,
            double expandedRadiusKm,
            List<RestaurantRecommendationItem> items,
            RestaurantRecommendationSource source,
            String fallbackReason,
            Map<String, Long> chatPreferenceCounts,
            String majorityCategory
    ) {
        return RestaurantRecommendationResponse.builder()
                .source(source.name())
                .center(LocationResponse.builder()
                        .latitude(center.latitude())
                        .longitude(center.longitude())
                        .build())
                .radiusKm(radiusKm)
                .expandedRadiusKm(expandedRadiusKm)
                .preferredFoodCategory(preferredCategory)
                .chatPreferredFoodCategoryCounts(chatPreferenceCounts)
                .majorityPreferredFoodCategory(majorityCategory)
                .recommendations(items)
                .fallbackReason(fallbackReason)
                .build();
    }

    private String fallbackReason(Map<String, Long> chatPreferenceCounts) {
        if (chatPreferenceCounts == null || chatPreferenceCounts.isEmpty()) {
            return "DB 점수 기준으로 추천합니다.";
        }
        return "참여자 선호도와 DB 점수 기준으로 추천합니다.";
    }

    private Long firstImageId(String targetType, Long targetId) {
        return fileAttachmentRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .stream()
                .map(FileAttachment::getFileId)
                .findFirst()
                .orElse(null);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private double initialRadius(RestaurantRecommendationRequest request) {
        if (request.getRadiusKm() == null || request.getRadiusKm() <= 0) {
            return DEFAULT_RADIUS_KM;
        }
        return Math.min(request.getRadiusKm(), MAX_RADIUS_KM);
    }

    private String defaultReason(RestaurantCandidate candidate) {
        return "거리, 평점, 리뷰, 판매 중 메뉴를 기준으로 추천했습니다.";
    }

    private String defaultMatchedPreference(RestaurantCandidate candidate) {
        String category = candidate.getStore().getCategory();
        return category == null || category.isBlank()
                ? "선호 음식과 가까운 메뉴를 기준으로 비교했습니다."
                : category + " 카테고리와 메뉴 구성을 기준으로 비교했습니다.";
    }

    private String defaultDistanceText(RestaurantCandidate candidate) {
        if (candidate.getDistanceMeters() <= 700) {
            return "가까운 거리입니다.";
        }
        if (candidate.getDistanceMeters() <= 1500) {
            return "이동 가능한 거리입니다.";
        }
        return "주변 후보 중 비교적 가까운 편입니다.";
    }

    private String defaultReviewSummary(RestaurantCandidate candidate) {
        if (candidate.reviewCount() == 0) {
            return "아직 활성 리뷰가 많지 않습니다.";
        }
        return "활성 리뷰 " + candidate.reviewCount() + "개와 평균 평점을 반영했습니다.";
    }

    private String defaultCaution(RestaurantCandidate candidate) {
        if (candidate.reviewCount() == 0) {
            return "리뷰가 적어 실제 방문 전 상세 정보를 확인해주세요.";
        }
        return "";
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record CandidateSearchResult(
            List<RestaurantCandidate> candidates,
            double radiusKm
    ) {
    }
}
