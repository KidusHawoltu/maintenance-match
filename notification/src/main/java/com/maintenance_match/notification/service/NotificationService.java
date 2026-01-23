package com.maintenance_match.notification.service;

import com.maintenance_match.notification.dto.NotificationEvent;
import com.maintenance_match.notification.dto.NotificationResponse;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    void processNotification(NotificationEvent event);
    List<NotificationResponse> getMyNotifications(UUID userId);
    long getUnreadCount(UUID userId);
    void markAsRead(UUID notificationId, UUID userId);
    void markAllAsRead(UUID userId);
}
