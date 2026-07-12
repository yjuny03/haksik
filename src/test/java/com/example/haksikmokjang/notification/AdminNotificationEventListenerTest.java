package com.example.haksikmokjang.notification;

import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.notification.event.AdminNotificationEventListener;
import com.example.haksikmokjang.notification.event.AdminNotificationRequestedEvent;
import com.example.haksikmokjang.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminNotificationEventListenerTest {

    @Test
    void notificationFailureIsConsumedAfterOriginalTransactionCommit() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        Member admin = mock(Member.class);
        when(memberRepository.findAllByRole(MemberRole.ADMIN)).thenReturn(List.of(admin));
        org.mockito.Mockito.doThrow(new IllegalStateException("notification db failure"))
                .when(notificationService)
                .sendNotification(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        AdminNotificationEventListener listener = new AdminNotificationEventListener(
                memberRepository, notificationService
        );

        assertThatCode(() -> listener.handle(new AdminNotificationRequestedEvent(
                "ADMIN_OWNER_APPROVAL_PENDING", "제목", "내용", "OWNER_APPROVAL", 1L
        ))).doesNotThrowAnyException();
    }
}
