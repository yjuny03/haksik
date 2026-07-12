package com.example.haksikmokjang.ai.recommendation;

import com.example.haksikmokjang.ai.recommendation.service.RestaurantCandidate;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantCandidateQueryService;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantLocation;
import com.example.haksikmokjang.fileattachment.repository.FileAttachmentRepository;
import com.example.haksikmokjang.ownerpage.store.domain.BusinessStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import com.example.haksikmokjang.ownerpage.store.domain.MenuStatus;
import com.example.haksikmokjang.ownerpage.store.domain.ReviewStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Store;
import com.example.haksikmokjang.ownerpage.store.repository.MenuRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantCandidateQueryServiceTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private StoreReviewRepository storeReviewRepository;
    @Mock
    private FileAttachmentRepository fileAttachmentRepository;
    @InjectMocks
    private RestaurantCandidateQueryService queryService;

    @Test
    void excludesClosedStoresAndStoresWithoutOnSaleMenus() {
        Store open = store(1L, "열린가게", BusinessStatus.OPEN);
        Store closed = store(2L, "닫힌가게", BusinessStatus.CLOSED);
        Store noMenu = store(3L, "메뉴없는가게", BusinessStatus.OPEN);
        Menu openMenu = menu(10L, open);
        Menu closedMenu = menu(20L, closed);

        when(storeRepository.findNearbyStores(37.0, 127.0, 1.0)).thenReturn(List.of(open, closed, noMenu));
        when(menuRepository.findByStore_StoreIdInAndSalesStatus(List.of(1L, 3L), MenuStatus.ON_SALE))
                .thenReturn(List.of(openMenu, closedMenu));
        when(storeReviewRepository.findByStore_StoreIdInAndStatusOrderByCreatedAtDesc(List.of(1L, 3L), ReviewStatus.ACTIVE))
                .thenReturn(List.of());
        when(fileAttachmentRepository.findByTargetTypeAndTargetId(eq("STORE"), any())).thenReturn(List.of());

        List<RestaurantCandidate> candidates = queryService.findCandidates(new RestaurantLocation(37.0, 127.0), 1.0);

        assertThat(candidates).extracting(RestaurantCandidate::storeId).containsExactly(1L);
        verify(storeReviewRepository).findByStore_StoreIdInAndStatusOrderByCreatedAtDesc(List.of(1L, 3L), ReviewStatus.ACTIVE);
    }

    private Store store(Long id, String name, BusinessStatus status) {
        Store store = Store.builder()
                .name(name)
                .latitude(37.0)
                .longitude(127.0)
                .category("한식")
                .businessStatus(status)
                .build();
        ReflectionTestUtils.setField(store, "storeId", id);
        return store;
    }

    private Menu menu(Long id, Store store) {
        Menu menu = Menu.builder()
                .store(store)
                .name("대표메뉴")
                .price(7000)
                .salesStatus(MenuStatus.ON_SALE)
                .build();
        ReflectionTestUtils.setField(menu, "menuId", id);
        return menu;
    }
}
