package com.maintenance_match.notification.service;

import com.maintenance_match.notification.dto.NotificationRequest;

public interface NotificationService {
    void sendNotification(NotificationRequest notificationRequest);
}
