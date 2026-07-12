package com.example.haksikmokjang.notification.event;

import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationEventListener {

    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AdminNotificationRequestedEvent event) {
        try {
            memberRepository.findAllByRole(MemberRole.ADMIN).forEach(admin ->
                    notificationService.sendNotification(
                            admin,
                            event.notificationType(),
                            event.title(),
                            event.content(),
                            event.targetType(),
                            event.targetId()
                    )
            );
        } catch (RuntimeException exception) {
            log.error(
                    "관리자 알림 발송 실패. 원본 트랜잭션은 이미 커밋됨. type={} targetType={} targetId={}",
                    event.notificationType(), event.targetType(), event.targetId(), exception
            );
        }
    }
}
