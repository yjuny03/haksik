package com.example.haksikmokjang.ai.recommendation;

import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationRequest;
import com.example.haksikmokjang.ai.recommendation.dto.RestaurantRecommendationResponse;
import com.example.haksikmokjang.ai.recommendation.service.ClaudeRecommendationResult;
import com.example.haksikmokjang.ai.recommendation.service.ClaudeRestaurantRecommendationClient;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantCandidate;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantCandidateQueryService;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantRecommendationService;
import com.example.haksikmokjang.ai.recommendation.service.RestaurantScoringService;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomMemberRepository;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomRepository;
import com.example.haksikmokjang.fileattachment.repository.FileAttachmentRepository;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.repository.MemberLocationRepository;
import com.example.haksikmokjang.member.signup.user.domain.UserProfile;
import com.example.haksikmokjang.member.signup.user.repository.UserProfileRepository;
import com.example.haksikmokjang.ownerpage.store.domain.BusinessStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Menu;
import com.example.haksikmokjang.ownerpage.store.domain.MenuStatus;
import com.example.haksikmokjang.ownerpage.store.domain.ReviewStatus;
import com.example.haksikmokjang.ownerpage.store.domain.Store;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantRecommendationServiceTest {

    @Mock
    private RestaurantCandidateQueryService candidateQueryService;
    @Mock
    private ClaudeRestaurantRecommendationClient claudeClient;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private MemberLocationRepository memberLocationRepository;
    @Mock
    private FileAttachmentRepository fileAttachmentRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    private RestaurantRecommendationService recommendationService;
    private Member member;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        recommendationService = new RestaurantRecommendationService(
                candidateQueryService,
                new RestaurantScoringService(),
                claudeClient,
                userProfileRepository,
                memberLocationRepository,
                fileAttachmentRepository,
                chatRoomRepository,
                chatRoomMemberRepository
        );
        member = Member.builder().loginId("user1").build();
        profile = UserProfile.builder()
                .member(member)
                .name("사용자")
                .nickname("nick")
                .department("컴공")
                .preferredFoodCategory("한식")
                .build();
        when(userProfileRepository.findByMember(member)).thenReturn(Optional.of(profile));
        lenient().when(fileAttachmentRepository.findByTargetTypeAndTargetId(eq("MENU"), any())).thenReturn(List.of());
    }

    @Test
    void expandsRadiusWhenCandidatesAreInsufficient() {
        RestaurantCandidate candidate = candidate(1L, "한식집", "한식", 500, 10L);
        when(candidateQueryService.findCandidates(any(), eq(1.0))).thenReturn(List.of());
        when(candidateQueryService.findCandidates(any(), eq(2.0))).thenReturn(List.of(
                candidate,
                candidate(2L, "분식집", "한식", 700, 20L),
                candidate(3L, "국밥집", "한식", 900, 30L)
        ));
        when(claudeClient.recommend(any(), any(), any(), any(), any(), eq(3))).thenReturn(Optional.empty());

        RestaurantRecommendationResponse response = recommendationService.recommend(member, request());

        assertThat(response.getSource()).isEqualTo("DB_FALLBACK");
        assertThat(response.getExpandedRadiusKm()).isEqualTo(2.0);
        assertThat(response.getRecommendations()).hasSize(3);
    }

    @Test
    void unknownClaudeStoreIdFallsBackToDbRanking() {
        RestaurantCandidate candidate = candidate(1L, "한식집", "한식", 500, 10L);
        when(candidateQueryService.findCandidates(any(), eq(1.0))).thenReturn(List.of(
                candidate,
                candidate(2L, "분식집", "한식", 700, 20L),
                candidate(3L, "국밥집", "한식", 900, 30L)
        ));
        when(claudeClient.recommend(any(), any(), any(), any(), any(), eq(3))).thenReturn(Optional.of(
                new ClaudeRecommendationResult(List.of(
                        new ClaudeRecommendationResult.Item(
                                999L, 10L, 1, "없는 가게", "한식", "가까움", "리뷰", ""
                        )
                ))
        ));

        RestaurantRecommendationResponse response = recommendationService.recommend(member, request());

        assertThat(response.getSource()).isEqualTo("DB_FALLBACK");
        assertThat(response.getFallbackReason()).isEqualTo("CLAUDE_UNAVAILABLE");
        assertThat(response.getRecommendations().get(0).getStoreId()).isEqualTo(1L);
    }

    @Test
    void claudeFailureReturnsDbFallback() {
        RestaurantCandidate candidate = candidate(1L, "한식집", "한식", 500, 10L);
        when(candidateQueryService.findCandidates(any(), eq(1.0))).thenReturn(List.of(
                candidate,
                candidate(2L, "분식집", "한식", 700, 20L),
                candidate(3L, "국밥집", "한식", 900, 30L)
        ));
        when(claudeClient.recommend(any(), any(), any(), any(), any(), eq(3))).thenReturn(Optional.empty());

        RestaurantRecommendationResponse response = recommendationService.recommend(member, request());

        assertThat(response.getSource()).isEqualTo("DB_FALLBACK");
        assertThat(response.getRecommendations()).isNotEmpty();
    }

    @Test
    void chatbotPersonalRecommendationUsesDbStoreAndMenuWhenClaudeFails() {
        RestaurantCandidate candidate = candidate(1L, "인덕 제육하우스", "한식", 500, 10L);
        when(candidateQueryService.findCandidates(any(), eq(1.0))).thenReturn(List.of(
                candidate,
                candidate(2L, "분식집", "분식", 700, 20L),
                candidate(3L, "국밥집", "한식", 900, 30L)
        ));
        when(claudeClient.recommend(any(), any(), any(), any(), any(), eq(1))).thenReturn(Optional.empty());

        String answer = recommendationService.recommendForChatbot(member, "한식", 37.0, 127.0);

        assertThat(answer).contains("내 선호도 기준", "인덕 제육하우스", "제육덮밥");
    }

    @Test
    void chatbotPersonalRecommendationRequiresLocationWhenNoRequestOrSavedLocation() {
        when(memberLocationRepository.findByUserProfile(profile)).thenReturn(Optional.empty());

        String answer = recommendationService.recommendForChatbot(member, "한식");

        assertThat(answer).contains("현재 위치가 필요");
    }

    private RestaurantRecommendationRequest request() {
        return RestaurantRecommendationRequest.builder()
                .lat(37.0)
                .lng(127.0)
                .limit(3)
                .build();
    }

    private RestaurantCandidate candidate(Long storeId, String storeName, String category, int distanceMeters, Long menuId) {
        Store store = Store.builder()
                .name(storeName)
                .category(category)
                .latitude(37.0)
                .longitude(127.0)
                .businessStatus(BusinessStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(store, "storeId", storeId);

        Menu menu = Menu.builder()
                .store(store)
                .name("제육덮밥")
                .price(7000)
                .salesStatus(MenuStatus.ON_SALE)
                .build();
        ReflectionTestUtils.setField(menu, "menuId", menuId);

        StoreReview review = StoreReview.builder()
                .store(store)
                .rating(5)
                .content("맛있고 양 많아요")
                .status(ReviewStatus.ACTIVE)
                .build();

        return RestaurantCandidate.builder()
                .store(store)
                .menus(List.of(menu))
                .reviews(List.of(review))
                .distanceMeters(distanceMeters)
                .build();
    }
}
