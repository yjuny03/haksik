package com.example.haksikmokjang.notification.event;

public record AdminNotificationRequestedEvent(
        String notificationType,
        String title,
        String content,
        String targetType,
        Long targetId
) {
}
