package com.example.haksikmokjang.ai.recommendation.service;

import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RestaurantScoringService {

    private static final List<String> POSITIVE_REVIEW_KEYWORDS = List.of(
            "맛있", "친절", "가성비", "양 많", "재방문", "깔끔", "빠르"
    );

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "한식", List.of("김치", "찌개", "덮밥", "국밥", "비빔", "제육"),
            "중식", List.of("짜장", "짬뽕", "탕수", "마라"),
            "일식", List.of("돈카츠", "초밥", "라멘", "우동", "덮밥"),
            "양식", List.of("파스타", "피자", "스테이크", "버거", "샐러드")
    );

    public RestaurantScore score(RestaurantCandidate candidate, String preferredCategory, String requestedCategory) {
        return score(candidate, preferredCategory, requestedCategory, Map.of(), null, null);
    }

    public RestaurantScore score(
            RestaurantCandidate candidate,
            String preferredCategory,
            String requestedCategory,
            Map<String, Long> chatPreferenceCounts,
            String majorityCategory,
            Menu recommendedMenu
    ) {
        double distanceScore = distanceScore(candidate.getDistanceMeters());
        double preferredCategoryScore = preferredCategoryScore(candidate, preferredCategory);
        double requestedCategoryScore = requestedCategoryScore(candidate, requestedCategory);
        double ratingScore = ratingScore(candidate.averageRating(), candidate.reviewCount());
        double reviewCountScore = reviewCountScore(candidate.reviewCount());
        double recentReviewScore = recentReviewScore(candidate.getReviews());
        double menuScore = menuScore(candidate.getMenus());
        double chatPreferenceScore = chatPreferenceScore(candidate, chatPreferenceCounts);
        double majorityPreferenceScore = majorityPreferenceScore(candidate, majorityCategory);
        double recommendedMenuScore = recommendedMenuScore(candidate, recommendedMenu);

        double total = distanceScore
                + preferredCategoryScore
                + requestedCategoryScore
                + ratingScore
                + reviewCountScore
                + recentReviewScore
                + menuScore
                + chatPreferenceScore
                + majorityPreferenceScore
                + recommendedMenuScore;

        return new RestaurantScore(
                round(total),
                distanceScore,
                preferredCategoryScore,
                requestedCategoryScore,
                ratingScore,
                reviewCountScore,
                recentReviewScore,
                menuScore,
                chatPreferenceScore,
                majorityPreferenceScore,
                recommendedMenuScore
        );
    }

    private double distanceScore(int distanceMeters) {
        if (distanceMeters <= 300) {
            return 30;
        }
        if (distanceMeters <= 700) {
            return 22;
        }
        if (distanceMeters <= 1000) {
            return 15;
        }
        if (distanceMeters <= 2000) {
            return 8;
        }
        return 3;
    }

    private double preferredCategoryScore(RestaurantCandidate candidate, String preferredCategory) {
        if (isBlank(preferredCategory)) {
            return 0;
        }
        if (equalsText(candidate.getStore().getCategory(), preferredCategory)) {
            return 20;
        }
        return hasCategoryMenuKeyword(candidate.getMenus(), preferredCategory) ? 12 : 0;
    }

    private double requestedCategoryScore(RestaurantCandidate candidate, String requestedCategory) {
        if (isBlank(requestedCategory)) {
            return 0;
        }
        return equalsText(candidate.getStore().getCategory(), requestedCategory) ? 10 : 0;
    }

    private boolean hasCategoryMenuKeyword(List<Menu> menus, String category) {
        List<String> keywords = CATEGORY_KEYWORDS.getOrDefault(category, List.of());
        if (keywords.isEmpty()) {
            return false;
        }
        return menus.stream()
                .map(Menu::getName)
                .filter(name -> name != null)
                .anyMatch(name -> keywords.stream().anyMatch(name::contains));
    }

    private double ratingScore(double averageRating, int reviewCount) {
        if (reviewCount <= 0) {
            return 8;
        }
        if (averageRating >= 4.5) {
            return 20;
        }
        if (averageRating >= 4.0) {
            return 16;
        }
        if (averageRating >= 3.5) {
            return 12;
        }
        return 6;
    }

    private double reviewCountScore(int reviewCount) {
        if (reviewCount >= 20) {
            return 10;
        }
        if (reviewCount >= 10) {
            return 8;
        }
        if (reviewCount >= 5) {
            return 5;
        }
        if (reviewCount >= 1) {
            return 3;
        }
        return 0;
    }

    private double recentReviewScore(List<StoreReview> reviews) {
        long hitCount = reviews.stream()
                .limit(5)
                .map(StoreReview::getContent)
                .filter(content -> content != null)
                .map(content -> content.toLowerCase(Locale.ROOT))
                .filter(content -> POSITIVE_REVIEW_KEYWORDS.stream().anyMatch(content::contains))
                .count();
        return Math.min(5, hitCount);
    }

    private double menuScore(List<Menu> menus) {
        int count = menus == null ? 0 : menus.size();
        if (count >= 5) {
            return 5;
        }
        if (count >= 2) {
            return 3;
        }
        if (count == 1) {
            return 1;
        }
        return 0;
    }

    private double chatPreferenceScore(RestaurantCandidate candidate, Map<String, Long> preferenceCounts) {
        if (preferenceCounts == null || preferenceCounts.isEmpty()) {
            return 0;
        }

        return preferenceCounts.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .mapToDouble(entry -> {
                    double matchScore = 0;
                    if (equalsText(candidate.getStore().getCategory(), entry.getKey())) {
                        matchScore = 8;
                    } else if (hasCategoryMenuKeyword(candidate.getMenus(), entry.getKey())) {
                        matchScore = 5;
                    }
                    return Math.min(24, matchScore * entry.getValue());
                })
                .sum();
    }

    private double majorityPreferenceScore(RestaurantCandidate candidate, String majorityCategory) {
        if (isBlank(majorityCategory)) {
            return 0;
        }
        if (equalsText(candidate.getStore().getCategory(), majorityCategory)) {
            return 12;
        }
        return hasCategoryMenuKeyword(candidate.getMenus(), majorityCategory) ? 7 : 0;
    }

    private double recommendedMenuScore(RestaurantCandidate candidate, Menu recommendedMenu) {
        if (recommendedMenu == null || recommendedMenu.getName() == null) {
            return 0;
        }
        boolean belongsToCandidate = candidate.getMenus() != null
                && candidate.getMenus().stream()
                .anyMatch(menu -> recommendedMenu.getMenuId() != null
                        && recommendedMenu.getMenuId().equals(menu.getMenuId()));
        if (!belongsToCandidate) {
            return 0;
        }
        String storeCategory = candidate.getStore().getCategory();
        return hasCategoryMenuKeyword(List.of(recommendedMenu), storeCategory) ? 6 : 3;
    }

    private boolean equalsText(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
