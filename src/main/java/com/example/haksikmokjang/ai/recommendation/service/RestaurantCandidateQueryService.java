package com.example.haksikmokjang.ai.recommendation.service;

import com.example.haksikmokjang.fileattachment.domain.FileAttachment;
import com.example.haksikmokjang.fileattachment.repository.FileAttachmentRepository;
import com.example.haksikmokjang.ownerpage.store.domain.BusinessStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import com.example.haksikmokjang.ownerpage.store.domain.MenuStatus;
import com.example.haksikmokjang.ownerpage.store.domain.ReviewStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Store;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import com.example.haksikmokjang.ownerpage.store.repository.MenuRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantCandidateQueryService {

    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final FileAttachmentRepository fileAttachmentRepository;

    public List<RestaurantCandidate> findCandidates(RestaurantLocation center, double radiusKm) {
        List<Store> stores = storeRepository.findNearbyStores(center.latitude(), center.longitude(), radiusKm).stream()
                .filter(store -> store.getLatitude() != null && store.getLongitude() != null)
                .filter(store -> store.getBusinessStatus() != BusinessStatus.CLOSED)
                .toList();

        if (stores.isEmpty()) {
            return List.of();
        }

        List<Long> storeIds = stores.stream().map(Store::getStoreId).toList();
        Map<Long, List<Menu>> menusByStoreId = menuRepository
                .findByStore_StoreIdInAndSalesStatus(storeIds, MenuStatus.ON_SALE)
                .stream()
                .collect(Collectors.groupingBy(menu -> menu.getStore().getStoreId()));

        Map<Long, List<StoreReview>> reviewsByStoreId = storeReviewRepository
                .findByStore_StoreIdInAndStatusOrderByCreatedAtDesc(storeIds, ReviewStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(review -> review.getStore().getStoreId()));

        return stores.stream()
                .map(store -> toCandidate(store, center, menusByStoreId, reviewsByStoreId))
                .filter(candidate -> !candidate.getMenus().isEmpty())
                .sorted(Comparator.comparingInt(RestaurantCandidate::getDistanceMeters))
                .toList();
    }

    private RestaurantCandidate toCandidate(
            Store store,
            RestaurantLocation center,
            Map<Long, List<Menu>> menusByStoreId,
            Map<Long, List<StoreReview>> reviewsByStoreId
    ) {
        Long storeId = store.getStoreId();
        return RestaurantCandidate.builder()
                .store(store)
                .menus(menusByStoreId.getOrDefault(storeId, List.of()))
                .reviews(reviewsByStoreId.getOrDefault(storeId, List.of()))
                .distanceMeters((int) Math.round(distanceMeters(
                        center.latitude(),
                        center.longitude(),
                        store.getLatitude(),
                        store.getLongitude()
                )))
                .storeImageId(firstImageId("STORE", storeId))
                .build();
    }

    private Long firstImageId(String targetType, Long targetId) {
        return fileAttachmentRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .stream()
                .map(FileAttachment::getFileId)
                .findFirst()
                .orElse(null);
    }

    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusMeters = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2)
                * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }
}
