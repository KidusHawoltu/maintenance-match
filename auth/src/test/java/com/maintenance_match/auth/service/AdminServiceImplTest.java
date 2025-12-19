package com.maintenance_match.auth.service;

import com.maintenance_match.auth.client.MatchingClient;
import com.maintenance_match.auth.client.NotificationClient;
import com.maintenance_match.auth.dto.AdminUserView;
import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.UserRepository;
import com.maintenance_match.auth.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchingClient matchingClient;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    void approveMaintainer_shouldActivateUserAndCreateProfile() {
        // Given
        UUID userId = UUID.randomUUID();
        User pendingUser = User.builder()
                .id(userId).email("pending@user.com").firstName("Pending").lastName("User")
                .role(Role.MAINTAINER).approvalStatus(ApprovalStatus.PENDING).isActive(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AdminUserView result = adminService.approveMaintainer(userId);

        // Then
        // Verify user state was updated
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);

        // Verify Feign client was called
        ArgumentCaptor<MatchingClient.CreateMaintainerProfileRequest> clientCaptor =
                ArgumentCaptor.forClass(MatchingClient.CreateMaintainerProfileRequest.class);
        verify(matchingClient).createMaintainerProfile(clientCaptor.capture());
        MatchingClient.CreateMaintainerProfileRequest capturedRequest = clientCaptor.getValue();
        assertThat(capturedRequest.getUserId()).isEqualTo(userId);
        assertThat(capturedRequest.getName()).isEqualTo("Pending User");

        ArgumentCaptor<NotificationClient.NotificationRequest> notificationCaptor =
                ArgumentCaptor.forClass(NotificationClient.NotificationRequest.class);
        verify(notificationClient).sendNotification(notificationCaptor.capture());
        NotificationClient.NotificationRequest capturedNotification = notificationCaptor.getValue();
        assertThat(capturedNotification.getUserId()).isEqualTo(userId);
        assertThat(capturedNotification.getMessage()).contains("Congratulations", "approved");

        // Verify the returned DTO
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void rejectMaintainer_shouldDeactivateUserAndSendNotification() {
        // Given
        UUID userId = UUID.randomUUID();
        User pendingUser = User.builder().id(userId).role(Role.MAINTAINER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        adminService.rejectMaintainer(userId);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);

        ArgumentCaptor<NotificationClient.NotificationRequest> notificationCaptor =
                ArgumentCaptor.forClass(NotificationClient.NotificationRequest.class);
        verify(notificationClient).sendNotification(notificationCaptor.capture());

        NotificationClient.NotificationRequest capturedNotification = notificationCaptor.getValue();
        assertThat(capturedNotification.getUserId()).isEqualTo(userId);
        assertThat(capturedNotification.getMessage()).contains("rejected");
    }
}
