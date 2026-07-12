package com.example.haksikmokjang.ai.recommendation.service;

import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import com.example.haksikmokjang.ownerpage.store.domain.Store;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RestaurantCandidate {
    private Store store;
    private List<Menu> menus;
    private List<StoreReview> reviews;
    private int distanceMeters;
    private Long storeImageId;
    private RestaurantScore score;
    private Menu recommendedMenu;

    public Long storeId() {
        return store.getStoreId();
    }

    public String storeName() {
        return store.getName();
    }

    public double averageRating() {
        if (reviews == null || reviews.isEmpty()) {
            return 0.0;
        }
        return reviews.stream()
                .filter(review -> review.getRating() != null)
                .mapToInt(StoreReview::getRating)
                .average()
                .orElse(0.0);
    }

    public int reviewCount() {
        return reviews == null ? 0 : reviews.size();
    }

    public Menu defaultMenu() {
        return menus == null || menus.isEmpty() ? null : menus.get(0);
    }

    public Menu recommendedMenuOrDefault() {
        return recommendedMenu == null ? defaultMenu() : recommendedMenu;
    }

    public boolean hasMenu(Long menuId) {
        return menuId != null
                && menus != null
                && menus.stream().anyMatch(menu -> menuId.equals(menu.getMenuId()));
    }

    public Menu menuById(Long menuId) {
        if (menuId == null || menus == null) {
            return null;
        }
        return menus.stream()
                .filter(menu -> menuId.equals(menu.getMenuId()))
                .findFirst()
                .orElse(null);
    }

    public RestaurantCandidate withScore(RestaurantScore score) {
        return RestaurantCandidate.builder()
                .store(store)
                .menus(menus)
                .reviews(reviews)
                .distanceMeters(distanceMeters)
                .storeImageId(storeImageId)
                .score(score)
                .recommendedMenu(recommendedMenu)
                .build();
    }

    public RestaurantCandidate withRecommendedMenu(Menu recommendedMenu) {
        return RestaurantCandidate.builder()
                .store(store)
                .menus(menus)
                .reviews(reviews)
                .distanceMeters(distanceMeters)
                .storeImageId(storeImageId)
                .score(score)
                .recommendedMenu(recommendedMenu)
                .build();
    }
}
