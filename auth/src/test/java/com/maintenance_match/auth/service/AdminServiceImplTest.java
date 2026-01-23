package com.maintenance_match.auth.service;

import com.maintenance_match.auth.dto.AdminUserView;
import com.maintenance_match.auth.dto.MaintainerProfileEvent;
import com.maintenance_match.auth.dto.NotificationChannel;
import com.maintenance_match.auth.dto.NotificationEvent;
import com.maintenance_match.auth.exception.ResourceNotFoundException;
import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.UserRepository;
import com.maintenance_match.auth.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AdminServiceImpl adminService;

    private final String NOTIF_TOPIC = "notif-topic";
    private final String MAINTAINER_TOPIC = "maintainer-topic";
    private UUID testId;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Set @Value fields manually
        ReflectionTestUtils.setField(adminService, "notificationTopic", NOTIF_TOPIC);
        ReflectionTestUtils.setField(adminService, "maintainerTopic", MAINTAINER_TOPIC);

        testId = UUID.randomUUID();
        testUser = User.builder()
                .id(testId)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.MAINTAINER)
                .approvalStatus(ApprovalStatus.PENDING)
                .isActive(false)
                .build();
    }

    @Test
    void approveMaintainer_shouldUpdateUserAndSendKafkaEvents() {
        // Given
        when(userRepository.findById(testId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        AdminUserView result = adminService.approveMaintainer(testId);

        // Then
        assertThat(result.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.isActive()).isTrue();

        // 1. Verify Maintainer Profile Creation Event
        verify(kafkaTemplate).send(eq(MAINTAINER_TOPIC), eq(testId.toString()), any(MaintainerProfileEvent.class));

        // 2. Verify Notifications (Should send 2: IN_APP and EMAIL)
        ArgumentCaptor<NotificationEvent> notifCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(kafkaTemplate, times(2)).send(eq(NOTIF_TOPIC), eq(testId.toString()), notifCaptor.capture());

        List<NotificationEvent> sentEvents = notifCaptor.getAllValues();
        assertThat(sentEvents).extracting(NotificationEvent::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);

        assertThat(sentEvents.get(0).getTemplate()).isEqualTo("maintainer-approved");
    }

    @Test
    void rejectMaintainer_shouldUpdateUserAndSendNotifEvents() {
        // Given
        when(userRepository.findById(testId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        AdminUserView result = adminService.rejectMaintainer(testId);

        // Then
        assertThat(result.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.isActive()).isFalse();

        // Verify no maintainer profile was created
        verify(kafkaTemplate, never()).send(eq(MAINTAINER_TOPIC), anyString(), any());

        // Verify Notifications
        verify(kafkaTemplate, times(2)).send(eq(NOTIF_TOPIC), eq(testId.toString()), any(NotificationEvent.class));
    }

    @Test
    void disableUser_shouldSetIsActiveToFalse() {
        // Given
        testUser.setActive(true);
        when(userRepository.findById(testId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        AdminUserView result = adminService.disableUser(testId);

        // Then
        assertThat(result.isActive()).isFalse();
        verify(userRepository).save(testUser);
        // Ensure no Kafka events for simple disabling (unless you decide to add them later)
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void findUserOrThrow_shouldThrowException_whenUserNotFound() {
        // Given
        when(userRepository.findById(testId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> adminService.approveMaintainer(testId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getPendingMaintainers_shouldReturnFilteredList() {
        // Given
        when(userRepository.findByApprovalStatus(ApprovalStatus.PENDING))
                .thenReturn(List.of(testUser));

        // When
        List<AdminUserView> result = adminService.getPendingMaintainers();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("test@example.com");
    }
}
