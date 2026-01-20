package com.maintenance_match.notification.service.impl;

import com.maintenance_match.notification.client.AuthClient;
import com.maintenance_match.notification.dto.NotificationRequest;
import com.maintenance_match.notification.dto.UserDto;
import com.maintenance_match.notification.model.Notification; // Import entity
import com.maintenance_match.notification.repository.NotificationRepository; // Import repository
import com.maintenance_match.notification.service.NotificationService;
import lombok.RequiredArgsConstructor; // Use this for constructor injection
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthClient authClient;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

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

        UUID recipientId = resolveRecipientId(notificationRequest);
        if (recipientId == null) {
            log.warn("No recipient ID found for notification {}. Skipping email.", notification.getId());
            return;
        }

        if (!mailEnabled) {
            log.info("Email sending disabled. Skipping email for notification {}.", notification.getId());
            return;
        }

        UserDto user = authClient.getUserById(recipientId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("No email found for user {}. Skipping email for notification {}.", recipientId, notification.getId());
            return;
        }

        sendEmail(user.getEmail(), "MaintenanceMatch Notification", notificationRequest.getMessage());
    }

    private UUID resolveRecipientId(NotificationRequest notificationRequest) {
        if (notificationRequest.getUserId() != null) {
            return notificationRequest.getUserId();
        }
        return notificationRequest.getMaintainerId();
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Sent email notification to {}", to);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}
