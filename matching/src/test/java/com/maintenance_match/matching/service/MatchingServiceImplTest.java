package com.maintenance_match.matching.service;

import com.maintenance_match.matching.TestUtils;
import com.maintenance_match.matching.client.AuthClient;
import com.maintenance_match.matching.client.NotificationClient;
import com.maintenance_match.matching.dto.*;
import com.maintenance_match.matching.exception.CustomAccessDeniedException;
import com.maintenance_match.matching.model.Job;
import com.maintenance_match.matching.model.JobStatus;
import com.maintenance_match.matching.model.Maintainer;
import com.maintenance_match.matching.repository.JobRepository;
import com.maintenance_match.matching.repository.MaintainerRepository;
import com.maintenance_match.matching.service.impl.MatchingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MatchingServiceImplTest {

    @Mock
    private MaintainerRepository maintainerRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuthClient authClient;

    @Mock
    JobRepository jobRepository;

    @InjectMocks
    private MatchingServiceImpl matchingService;

    @Test
    void findNearbyMaintainers_shouldQueryRepositoryAndReturnDtos() {
        // --- Given ---
        double lat = 9.0325;
        double lon = 38.7542;
        double radius = 1500.0;

        Maintainer maintainer1 = Maintainer.builder()
                .id(UUID.randomUUID())
                .name("Test Garage 1")
                .isAvailable(true)
                .location(TestUtils.createPoint(38.7550, 9.0330)) // lon, lat
                .build();

        Maintainer maintainer2 = Maintainer.builder()
                .id(UUID.randomUUID())
                .name("Test Garage 2")
                .isAvailable(true)
                .location(TestUtils.createPoint(38.7560, 9.0340)) // lon, lat
                .build();

        List<Maintainer> mockMaintainerList = List.of(maintainer1, maintainer2);

        when(maintainerRepository.findAvailableMaintainersWithinRadius(any(Point.class), anyDouble()))
                .thenReturn(mockMaintainerList);

        // --- When ---
        List<MaintainerDto> resultDtos = matchingService.findNearbyMaintainers(lat, lon, radius);

        // --- Then ---
        // Verify the repository method was called
        verify(maintainerRepository, times(1))
                .findAvailableMaintainersWithinRadius(any(Point.class), eq(radius)); // We can be specific with the radius

        // Assert the results
        assertThat(resultDtos).hasSize(2);
        assertThat(resultDtos.get(0).getId()).isEqualTo(maintainer1.getId());
        assertThat(resultDtos.get(0).getName()).isEqualTo("Test Garage 1");
        assertThat(resultDtos.get(1).getId()).isEqualTo(maintainer2.getId());
        assertThat(resultDtos.get(1).getName()).isEqualTo("Test Garage 2");
        // Verify the coordinate mapping (Y is latitude, X is longitude)
        assertThat(resultDtos.get(0).getLatitude()).isEqualTo(maintainer1.getLocation().getY());
        assertThat(resultDtos.get(0).getLongitude()).isEqualTo(maintainer1.getLocation().getX());
    }

    @Test
    void findNearbyMaintainers_whenRepositoryReturnsEmpty_shouldReturnEmptyList() {
        // --- Given ---
        when(maintainerRepository.findAvailableMaintainersWithinRadius(any(Point.class), anyDouble()))
                .thenReturn(List.of()); // Return an empty list

        // --- When ---
        List<MaintainerDto> resultDtos = matchingService.findNearbyMaintainers(9.0, 38.0, 1000);

        // --- Then ---
        assertThat(resultDtos).isNotNull().isEmpty();
        verify(maintainerRepository, times(1))
                .findAvailableMaintainersWithinRadius(any(Point.class), anyDouble());
    }

    @Test
    void createJob_shouldSaveAndReturnDto() {
        // --- Given ---
        UUID userId = UUID.randomUUID();
        MatchRequestDto matchRequest = MatchRequestDto.builder()
                .maintainerId(UUID.randomUUID())
                .problemDescription("Flat tire")
                .latitude(9.0)
                .longitude(38.0)
                .build();

        Maintainer availableMaintainer = Maintainer.builder()
                .id(matchRequest.getMaintainerId())
                .userId(UUID.randomUUID())
                .capacity(1)
                .activeJobs(0)
                .isAvailable(true)
                .build();

        when(maintainerRepository.findById(matchRequest.getMaintainerId())).thenReturn(Optional.of(availableMaintainer));

        // --- APPLY THE SAME FIX HERE ---
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(UUID.randomUUID()); // Simulate DB generating an ID
            return job;
        });
        when(maintainerRepository.save(any(Maintainer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // ---

        when(authClient.getUserById(any())).thenReturn(new UserDto());

        // --- When ---
        JobDto resultDto = matchingService.createJob(matchRequest, userId);

        // --- Then ---
        verify(jobRepository, times(1)).save(any(Job.class));

        ArgumentCaptor<Maintainer> maintainerCaptor = ArgumentCaptor.forClass(Maintainer.class);
        verify(maintainerRepository, times(1)).save(maintainerCaptor.capture());
        Maintainer savedMaintainer = maintainerCaptor.getValue();
        assertThat(savedMaintainer.getActiveJobs()).isEqualTo(1);
        assertThat(savedMaintainer.isAvailable()).isFalse(); // Because capacity was 1

        assertThat(resultDto).isNotNull();
        assertThat(resultDto.getId()).isNotNull();

        ArgumentCaptor<NotificationRequest> notificationCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient, times(2)).sendNotification(notificationCaptor.capture());

        List<NotificationRequest> capturedNotifications = notificationCaptor.getAllValues();
        assertThat(capturedNotifications)
                .extracting(NotificationRequest::getUserId)
                .containsExactlyInAnyOrder(userId, availableMaintainer.getUserId());
    }

    @Test
    void getMyJobs_asUser_shouldCallFindByUserId() {
        // Given
        UUID userId = UUID.randomUUID();
        when(jobRepository.findByUserId(userId)).thenReturn(List.of()); // Mock repository call

        // When
        matchingService.getMyJobs(userId, "USER");

        // Then
        verify(jobRepository, times(1)).findByUserId(userId);
        verify(maintainerRepository, never()).findByUserId(any()); // Ensure the maintainer path is not taken
    }

    @Test
    void getMyJobs_asMaintainer_shouldCallFindByMaintainerId() {
        // Given
        UUID maintainerUserId = UUID.randomUUID();
        Maintainer maintainerProfile = Maintainer.builder().id(UUID.randomUUID()).userId(maintainerUserId).build();
        when(maintainerRepository.findByUserId(maintainerUserId)).thenReturn(Optional.of(maintainerProfile));
        when(jobRepository.findByMaintainerId(maintainerProfile.getId())).thenReturn(List.of()); // Mock repo call

        // When
        matchingService.getMyJobs(maintainerUserId, "MAINTAINER");

        // Then
        verify(maintainerRepository, times(1)).findByUserId(maintainerUserId);
        verify(jobRepository, times(1)).findByMaintainerId(maintainerProfile.getId());
        verify(jobRepository, never()).findByUserId(any()); // Ensure the user path is not taken
    }

    @Test
    void terminateJob_byUser_shouldUpdateStatusAndDecrementMaintainerJobs() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID maintainerUserId = UUID.randomUUID();
        Maintainer maintainer = Maintainer.builder()
                .id(UUID.randomUUID()).userId(maintainerUserId).activeJobs(1).capacity(1).isAvailable(false).build();

        Job activeJob = Job.builder()
                .id(UUID.randomUUID()).userId(userId).maintainerId(maintainer.getId()).status(JobStatus.ACTIVE).build();

        when(jobRepository.findById(activeJob.getId())).thenReturn(Optional.of(activeJob));
        when(maintainerRepository.findById(maintainer.getId())).thenReturn(Optional.of(maintainer));

        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintainerRepository.save(any(Maintainer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock the auth client calls for DTO building
        when(authClient.getUserById(any())).thenReturn(new UserDto());

        // When: The user cancels the job
        matchingService.terminateJob(activeJob.getId(), userId, true);

        // Then
        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository, times(1)).save(jobCaptor.capture());
        Job savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(JobStatus.CANCELLED_BY_USER);
        assertThat(savedJob.getCompletedAt()).isNotNull();

        ArgumentCaptor<Maintainer> maintainerCaptor = ArgumentCaptor.forClass(Maintainer.class);
        verify(maintainerRepository, times(1)).save(maintainerCaptor.capture());
        Maintainer savedMaintainer = maintainerCaptor.getValue();
        assertThat(savedMaintainer.getActiveJobs()).isEqualTo(0);

        ArgumentCaptor<NotificationRequest> notificationCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient, times(2)).sendNotification(notificationCaptor.capture());

        List<NotificationRequest> capturedNotifications = notificationCaptor.getAllValues();
        assertThat(capturedNotifications)
                .extracting(NotificationRequest::getUserId)
                .containsExactlyInAnyOrder(userId, maintainerUserId);
        assertThat(capturedNotifications.get(0).getMessage()).contains("cancelled by the customer");
    }

    @Test
    void terminateJob_byUnauthorizedUser_shouldThrowAccessDeniedException() {
        // Given
        UUID authorizedUserId = UUID.randomUUID();
        UUID unauthorizedUserId = UUID.randomUUID(); // A different user
        Maintainer maintainer = Maintainer.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).build();
        Job activeJob = Job.builder().id(UUID.randomUUID()).userId(authorizedUserId).maintainerId(maintainer.getId()).status(JobStatus.ACTIVE).build();

        when(jobRepository.findById(activeJob.getId())).thenReturn(Optional.of(activeJob));
        when(maintainerRepository.findById(maintainer.getId())).thenReturn(Optional.of(maintainer));

        // When & Then
        assertThatThrownBy(() -> matchingService.terminateJob(activeJob.getId(), unauthorizedUserId, true))
                .isInstanceOf(CustomAccessDeniedException.class);
    }
}
