package com.maintenance_match.notification.service.impl;

import com.maintenance_match.notification.client.AuthClient;
import com.maintenance_match.notification.dto.*;
import com.maintenance_match.notification.exception.NotificationAccessDeniedException;
import com.maintenance_match.notification.exception.ResourceNotFoundException;
import com.maintenance_match.notification.model.Notification;
import com.maintenance_match.notification.repository.NotificationRepository;
import com.maintenance_match.notification.service.EmailService;
import com.maintenance_match.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthClient authClient;
    private final EmailService emailService;

    @Override
    @Transactional
    public void processNotification(NotificationEvent event) {
        log.info("Processing notification event {} for user {} on channel {}",
                event.getEventId(), event.getRecipientId(), event.getChannel());

        if (event.getChannel() == NotificationChannel.IN_APP) {
            handleInAppNotification(event);
        } else if (event.getChannel() == NotificationChannel.EMAIL) {
            handleEmailNotification(event);
        }
    }

    private void handleInAppNotification(NotificationEvent event) {
        // 1. Idempotency Check
        if (notificationRepository.existsByEventId(event.getEventId())) {
            log.warn("Duplicate In-App notification event {} detected. Skipping.", event.getEventId());
            return;
        }

        // 2. Save to DB
        Notification notification = Notification.builder()
                .eventId(event.getEventId())
                .recipientId(UUID.fromString(event.getRecipientId()))
                .message(event.getMessage())
                .isRead(false)
                .build();

        notificationRepository.save(notification);
        log.info("In-App notification saved.");
    }

    private void handleEmailNotification(NotificationEvent event) {
        // 1. Fetch User Data
        UserDto userDto;
        try {
            userDto = authClient.getUserById(UUID.fromString(event.getRecipientId()));
        } catch (Exception e) {
            log.error("Failed to fetch user details. Cannot send email.", e);
            throw e; // Rethrow to trigger Kafka retry
        }

        // 2. Send Email
        // TODO: check userDto.isEmailNotificationsEnabled()
        try {
            emailService.sendHtmlEmail(
                    userDto.getEmail(),
                    event.getSubject(),
                    event.getTemplate(),
                    event.getVariables()
            );
        } catch (Exception e) {
            log.error("Failed to send email. Kafka will retry.", e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(UUID userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID: " + notificationId));

        if (!notification.getRecipientId().equals(userId)) {
            throw new NotificationAccessDeniedException("You do not have permission to modify this notification.");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}