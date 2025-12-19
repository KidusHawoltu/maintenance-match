package com.maintenance_match.notification.service.impl;

import com.maintenance_match.notification.dto.NotificationRequest;
import com.maintenance_match.notification.model.Notification; // Import entity
import com.maintenance_match.notification.repository.NotificationRepository; // Import repository
import com.maintenance_match.notification.service.NotificationService;
import lombok.RequiredArgsConstructor; // Use this for constructor injection
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public void sendNotification(NotificationRequest notificationRequest) {
        log.info("Received request to create and send notification for user: {}", notificationRequest.getUserId());

        // Build the entity from the DTO
        Notification notification = Notification.builder()
                .recipientId(notificationRequest.getUserId())
                .message(notificationRequest.getMessage())
                .isRead(false)
                .build();

        notificationRepository.save(notification);

        log.info("Successfully saved notification {} to the database.", notification.getId());
    }
}
