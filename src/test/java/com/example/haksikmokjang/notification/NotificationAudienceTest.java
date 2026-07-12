package com.example.haksikmokjang.notification;

import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.notification.domain.Notification;
import com.example.haksikmokjang.notification.dto.NotificationResponse;
import com.example.haksikmokjang.notification.repository.NotificationRepository;
import com.example.haksikmokjang.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationAudienceTest {

    @Test
    void adminEndpointFiltersTypesWhileExistingUserEndpointStillReturnsUserNotifications() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        Member member = mock(Member.class);
        Notification userNotification = notification(member, "COMMUNITY", "POST");
        Notification adminNotification = notification(member, "ADMIN_AI_REVIEW_REQUIRED", "ADMIN_REPORT");
        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(member));
        when(notificationRepository.findByMemberOrderByCreatedAtDesc(member))
                .thenReturn(List.of(userNotification))
                .thenReturn(List.of(userNotification, adminNotification))
                .thenReturn(List.of(userNotification, adminNotification));
        NotificationService service = new NotificationService(notificationRepository, memberRepository);

        Map<String, Object> userResult = service.getMyNotifications("admin");
        Map<String, Object> adminResult = service.getAdminNotifications("admin");

        assertThat((List<?>) userResult.get("notifications")).hasSize(1);
        @SuppressWarnings("unchecked")
        List<NotificationResponse> adminNotifications =
                (List<NotificationResponse>) adminResult.get("notifications");
        assertThat(adminNotifications).hasSize(1);
        assertThat(adminNotifications.getFirst().getNotificationType())
                .isEqualTo("ADMIN_AI_REVIEW_REQUIRED");

        service.readAllAdminNotifications("admin");

        assertThat(adminNotification.getReadYn()).isEqualTo("Y");
        assertThat(userNotification.getReadYn()).isEqualTo("N");
    }

    private Notification notification(Member member, String type, String targetType) {
        return Notification.builder()
                .member(member)
                .notificationType(type)
                .title("제목")
                .content("내용")
                .targetType(targetType)
                .targetId(1L)
                .build();
    }
}
