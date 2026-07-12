package com.example.haksikmokjang.notification.dto;

import com.example.haksikmokjang.notification.domain.Notification;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationResponse {
    private Long notificationId;
    private String notificationType;
    private String title;
    private String content;
    private boolean read;
    private String targetType;
    private Long targetId;
    private String targetUrl;
    private LocalDateTime createdAt;

    public NotificationResponse(Notification noti) {
        this.notificationId = noti.getNotificationId();
        this.notificationType = noti.getNotificationType();
        this.title = noti.getTitle();
        this.content = noti.getContent();
        this.read = "Y".equals(noti.getReadYn());
        this.targetType = noti.getTargetType();
        this.targetId = noti.getTargetId();
        this.createdAt = noti.getCreatedAt();

        if ("POST".equals(noti.getTargetType())) {
            this.targetUrl = "/api/view/community/" + noti.getTargetId();
        } else if ("MATCHING".equals(noti.getTargetType())) {
            this.targetUrl = "/api/view/user/main";
        } else if ("RESERVATION".equals(noti.getTargetType())) {
            this.targetUrl = "/api/view/reservations/" + noti.getTargetId();
        } else if ("CHAT_ROOM".equals(noti.getTargetType())) {
            this.targetUrl = "/api/view/user/chat/rooms/" + noti.getTargetId();
        } else if ("CHAT_INVITE".equals(noti.getTargetType())) {
            this.targetUrl = "/api/view/user/chat/rooms/" + noti.getTargetId();
        } else if ("ADMIN_REPORT".equals(noti.getTargetType())) {
            this.targetUrl = "/api/view/admin/reports?reportId=" + noti.getTargetId();
        } else if ("OWNER_APPROVAL".equals(noti.getTargetType())) {
            this.targetUrl = "/api/view/admin/owners?status=PENDING";
        } else {
            this.targetUrl = "#";
        }
    }
}
