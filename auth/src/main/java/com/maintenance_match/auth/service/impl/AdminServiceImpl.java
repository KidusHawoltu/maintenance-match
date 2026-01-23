package com.maintenance_match.auth.service.impl;

import com.maintenance_match.auth.dto.AdminUserView;
import com.maintenance_match.auth.dto.MaintainerProfileEvent;
import com.maintenance_match.auth.dto.NotificationChannel;
import com.maintenance_match.auth.dto.NotificationEvent;
import com.maintenance_match.auth.exception.ResourceNotFoundException;
import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.UserRepository;
import com.maintenance_match.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.notification-send}")
    private String notificationTopic;

    @Value("${app.kafka.topics.maintainer-profile-creation}")
    private String maintainerTopic;

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserView> getAllUsers() {
        return userRepository.findAll().stream()
                .map(AdminUserView::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserView> getPendingMaintainers() {
        return userRepository.findByApprovalStatus(ApprovalStatus.PENDING).stream()
                .map(AdminUserView::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AdminUserView approveMaintainer(UUID userId) {
        User user = findUserOrThrow(userId);
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        user.setActive(true);
        User savedUser = userRepository.save(user);

        MaintainerProfileEvent profileEvent = MaintainerProfileEvent.builder()
                .userId(savedUser.getId())
                .name(savedUser.getFirstName() + " " + savedUser.getLastName())
                .email(savedUser.getEmail())
                .build();

        kafkaTemplate.send(maintainerTopic, savedUser.getId().toString(), profileEvent);

        String message = "Congratulations! Your maintainer account has been approved. You can now log in and start accepting jobs.";

        // --- SEND APPROVAL NOTIFICATION ---
        sendNotification(
                userId,
                "Application Approved!",
                "Congratulations! Your maintainer account has been approved.",
                "maintainer-approved",
                Map.of("name", savedUser.getFirstName())
        );

        return AdminUserView.fromEntity(savedUser);
    }

    @Override
    @Transactional
    public AdminUserView rejectMaintainer(UUID userId) {
        User user = findUserOrThrow(userId);
        user.setApprovalStatus(ApprovalStatus.REJECTED);
        user.setActive(false);
        User savedUser = userRepository.save(user);

        String message = "We regret to inform you that your maintainer registration has been rejected at this time.";

        // --- SEND REJECTION NOTIFICATION ---
        sendNotification(
                userId,
                "Application Status Update",
                "Your maintainer application has been rejected.",
                "maintainer-rejected",
                Map.of("name", savedUser.getFirstName())
        );

        return AdminUserView.fromEntity(savedUser);
    }

    @Override
    @Transactional
    public AdminUserView disableUser(UUID userId) {
        User user = findUserOrThrow(userId);
        user.setActive(false);
        User savedUser = userRepository.save(user);
        return AdminUserView.fromEntity(savedUser);
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    // --- HELPER METHOD TO SEND SPLIT EVENTS ---
    private void sendNotification(UUID userId, String subject, String textMessage, String template, Map<String, Object> vars) {
        String recipientId = userId.toString();

        // 1. In-App Notification (DB Persist)
        NotificationEvent inAppEvent = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .recipientId(recipientId)
                .channel(NotificationChannel.IN_APP)
                .message(textMessage)
                .build();
        kafkaTemplate.send(notificationTopic, recipientId, inAppEvent);

        // 2. Email Notification
        NotificationEvent emailEvent = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .recipientId(recipientId)
                .channel(NotificationChannel.EMAIL)
                .subject(subject)
                .template(template)
                .variables(vars)
                .build();
        kafkaTemplate.send(notificationTopic, recipientId, emailEvent);
    }
}
