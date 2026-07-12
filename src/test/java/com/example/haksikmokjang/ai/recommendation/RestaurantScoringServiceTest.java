package com.example.haksikmokjang.ai.recommendation;

import com.example.haksikmokjang.ai.recommendation.service.RestaurantCandidate;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantScore;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantScoringService;
import com.example.haksikmokjang.ownerpage.store.domain.BusinessStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import com.example.haksikmokjang.ownerpage.store.domain.MenuStatus;
import com.example.haksikmokjang.ownerpage.store.domain.ReviewStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Store;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantScoringServiceTest {

    private final RestaurantScoringService scoringService = new RestaurantScoringService();

    @Test
    void preferredCategoryMatchRaisesScore() {
        RestaurantCandidate korean = candidate("한식", 400, List.of(menu("제육덮밥")), List.of(review(5, "맛있고 친절합니다")));
        RestaurantCandidate western = candidate("양식", 400, List.of(menu("파스타")), List.of(review(5, "맛있고 친절합니다")));

        RestaurantScore koreanScore = scoringService.score(korean, "한식", null);
        RestaurantScore westernScore = scoringService.score(western, "한식", null);

        assertThat(koreanScore.total()).isGreaterThan(westernScore.total());
        assertThat(koreanScore.preferredCategoryScore()).isEqualTo(20);
    }

    @Test
    void ratingAndReviewCountAffectScore() {
        RestaurantCandidate highRating = candidate("한식", 400, List.of(menu("김치찌개")),
                List.of(review(5, "맛있습니다"), review(5, "재방문합니다"), review(4, "깔끔합니다")));
        RestaurantCandidate lowRating = candidate("한식", 400, List.of(menu("김치찌개")),
                List.of(review(2, "보통입니다")));

        assertThat(scoringService.score(highRating, null, null).total())
                .isGreaterThan(scoringService.score(lowRating, null, null).total());
    }

    @Test
    void chatRoomPreferenceMajorityRaisesMatchingCategoryScore() {
        RestaurantCandidate korean = candidate("한식", 400, List.of(menu("제육덮밥")), List.of(review(5, "맛있습니다")));
        RestaurantCandidate chinese = candidate("중식", 400, List.of(menu("짜장면")), List.of(review(5, "맛있습니다")));

        RestaurantScore koreanScore = scoringService.score(korean, null, null, Map.of("한식", 2L, "중식", 1L), "한식", korean.defaultMenu());
        RestaurantScore chineseScore = scoringService.score(chinese, null, null, Map.of("한식", 2L, "중식", 1L), "한식", chinese.defaultMenu());

        assertThat(koreanScore.total()).isGreaterThan(chineseScore.total());
        assertThat(koreanScore.chatPreferenceScore()).isGreaterThan(chineseScore.chatPreferenceScore());
        assertThat(koreanScore.majorityPreferenceScore()).isEqualTo(12);
    }

    private RestaurantCandidate candidate(String category, int distanceMeters, List<Menu> menus, List<StoreReview> reviews) {
        Store store = Store.builder()
                .name("테스트가게")
                .category(category)
                .latitude(37.0)
                .longitude(127.0)
                .businessStatus(BusinessStatus.OPEN)
                .build();
        return RestaurantCandidate.builder()
                .store(store)
                .menus(menus)
                .reviews(reviews)
                .distanceMeters(distanceMeters)
                .build();
    }

    private Menu menu(String name) {
        return Menu.builder()
                .name(name)
                .price(7000)
                .salesStatus(MenuStatus.ON_SALE)
                .build();
    }

    private StoreReview review(int rating, String content) {
        return StoreReview.builder()
                .rating(rating)
                .content(content)
                .status(ReviewStatus.ACTIVE)
                .build();
    }
}
