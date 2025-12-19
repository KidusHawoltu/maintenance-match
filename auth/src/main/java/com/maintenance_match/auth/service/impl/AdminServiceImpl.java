package com.maintenance_match.auth.service.impl;

import com.maintenance_match.auth.client.MatchingClient;
import com.maintenance_match.auth.client.NotificationClient;
import com.maintenance_match.auth.dto.AdminUserView;
import com.maintenance_match.auth.exception.ResourceNotFoundException;
import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.UserRepository;
import com.maintenance_match.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final MatchingClient matchingClient;
    private final NotificationClient notificationClient;

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

        // --- Inter-service call ---
        MatchingClient.CreateMaintainerProfileRequest request = new MatchingClient.CreateMaintainerProfileRequest();
        request.setUserId(savedUser.getId());
        request.setName(savedUser.getFirstName() + " " + savedUser.getLastName());
        matchingClient.createMaintainerProfile(request);

        String message = "Congratulations! Your maintainer account has been approved. You can now log in and start accepting jobs.";
        sendNotification(userId, message);

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
        sendNotification(userId, message);

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

    private void sendNotification(UUID recipientId, String message) {
        try {
            notificationClient.sendNotification(
                    new NotificationClient.NotificationRequest(recipientId, null, message)
            );
        } catch (Exception e) {
            log.error("Failed to send approval/rejection notification to user {}: {}", recipientId, e.getMessage());
        }
    }
}
