package com.maintenance_match.matching.service.impl;

import com.maintenance_match.matching.client.AuthClient;
import com.maintenance_match.matching.client.NotificationClient;
import com.maintenance_match.matching.dto.*;
import com.maintenance_match.matching.exception.BadRequestException;
import com.maintenance_match.matching.exception.ResourceNotFoundException;
import com.maintenance_match.matching.exception.CustomAccessDeniedException;
import com.maintenance_match.matching.model.Job;
import com.maintenance_match.matching.model.JobStatus;
import com.maintenance_match.matching.model.Maintainer;
import com.maintenance_match.matching.repository.JobRepository;
import com.maintenance_match.matching.repository.MaintainerRepository;
import com.maintenance_match.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingServiceImpl implements MatchingService {

    private final JobRepository jobRepository;
    private final MaintainerRepository maintainerRepository;
    private final NotificationClient notificationClient;
    private final AuthClient authClient;

    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    @Transactional(readOnly = true)
    public List<MaintainerDto> findNearbyMaintainers(double latitude, double longitude, double radiusInMeters) {
        log.info("Searching for maintainers near ({}, {}) within {} meters", latitude, longitude, radiusInMeters);

        Point searchCenter = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        return maintainerRepository.findAvailableMaintainersWithinRadius(searchCenter, radiusInMeters)
                .stream()
                .map(MaintainerDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JobDto createJob(MatchRequestDto request, UUID userId) {
        Maintainer maintainer = maintainerRepository.findById(request.getMaintainerId())
                .orElseThrow(() -> new ResourceNotFoundException("Maintainer not found with ID: " + request.getMaintainerId()));

        // --- Availability Check ---
        if (!maintainer.isAvailable() || maintainer.getActiveJobs() >= maintainer.getCapacity()) {
            throw new BadRequestException("Maintainer is currently not available for new jobs.");
        }

        // --- Update Maintainer State ---
        maintainer.setActiveJobs(maintainer.getActiveJobs() + 1);
        if (maintainer.getActiveJobs() >= maintainer.getCapacity()) {
            maintainer.setAvailable(false);
        }
        maintainerRepository.save(maintainer);

        // --- Create and Save the Job ---
        Point userLocation = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));
        Job newJob = Job.builder()
                .userId(userId)
                .maintainerId(maintainer.getId())
                .status(JobStatus.ACTIVE)
                .problemDescription(request.getProblemDescription())
                .userLocation(userLocation)
                .build();
        Job savedJob = jobRepository.save(newJob);

        log.info("Created new job {} for user {} with maintainer {}", savedJob.getId(), userId, maintainer.getId());

        // Notify the user who created the job
        sendNotification(
                userId,
                "Your match with maintainer '" + maintainer.getName() + "' has been confirmed. You can now contact them."
        );

        // Notify the maintainer who was matched
        sendNotification(
                maintainer.getUserId(),
                "You have a new match! A user needs help with: '" + savedJob.getProblemDescription() + "'."
        );

        return buildJobDto(savedJob);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDto> getMyJobs(UUID userId, String role) {
        log.info("Fetching jobs for user {} with role {}", userId, role);

        List<Job> jobs;
        if ("MAINTAINER".equalsIgnoreCase(role)) {
            // Find the maintainer profile associated with this user ID
            Maintainer maintainer = maintainerRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Maintainer profile not found for user ID: " + userId));
            // Find jobs by the maintainer profile ID
            jobs = jobRepository.findByMaintainerId(maintainer.getId());
        } else if ("USER".equalsIgnoreCase(role)) {
            jobs = jobRepository.findByUserId(userId);
        } else {
            log.warn("Attempted to fetch jobs for user {} with unknown role {}", userId, role);
            return Collections.emptyList();
        }

        if (jobs.isEmpty()) {
            return Collections.emptyList();
        }

        return jobs.stream()
                .map(this::buildJobDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JobDto terminateJob(UUID jobId, UUID userId, boolean isCancelled) {
        log.info("User {} is attempting to terminate job {}", userId, jobId);

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with ID: " + jobId));

        // --- Authorization Check ---
        // Find the maintainer associated with the job to get their userId
        Maintainer maintainer = maintainerRepository.findById(job.getMaintainerId())
                .orElseThrow(() -> new ResourceNotFoundException("Maintainer for job not found."));

        UUID maintainerUserId = maintainer.getUserId();

        // The user terminating the request must be either the customer or the maintainer
        if (!userId.equals(job.getUserId()) && !userId.equals(maintainerUserId)) {
            throw new CustomAccessDeniedException("User is not authorized to terminate this job.");
        }

        if (job.getStatus() != JobStatus.ACTIVE) {
            throw new BadRequestException("Job is not in an active state and cannot be terminated.");
        }

        // --- Update Job Status ---
        if (isCancelled) {
            // Determine who cancelled it
            job.setStatus(userId.equals(job.getUserId()) ? JobStatus.CANCELLED_BY_USER : JobStatus.CANCELLED_BY_MAINTAINER);
        } else {
            job.setStatus(JobStatus.COMPLETED);
        }
        job.setCompletedAt(LocalDateTime.now());
        Job updatedJob = jobRepository.save(job);

        // A spot has freed up, so decrement the active job count and make them available again.
        maintainer.setActiveJobs(Math.max(0, maintainer.getActiveJobs() - 1));
        maintainerRepository.save(maintainer);


        log.info("Job {} has been terminated with status {}. Maintainer {} now has {} active jobs.",
                updatedJob.getId(), updatedJob.getStatus(), maintainer.getId(), maintainer.getActiveJobs());

        UUID customerId = updatedJob.getUserId();
        String terminatorRole = userId.equals(customerId) ? "The customer" : "The maintainer";
        if (isCancelled) {
            sendNotification(customerId, "Job '"+ updatedJob.getProblemDescription() +"' has been cancelled by " + terminatorRole.toLowerCase() + ".");
            sendNotification(maintainerUserId, "Job '"+ updatedJob.getProblemDescription() +"' has been cancelled by " + terminatorRole.toLowerCase() + ".");
            log.info("Sent cancellation notifications for job {}", updatedJob.getId());
        } else {
            sendNotification(customerId, "Your job '"+ updatedJob.getProblemDescription() +"' has been marked as completed.");
            sendNotification(maintainerUserId, "Your job '"+ updatedJob.getProblemDescription() +"' has been marked as completed.");
            log.info("Sent completion notifications for job {}", updatedJob.getId());
        }

        return buildJobDto(updatedJob);
    }

    /**
     * Helper method to build a rich JobDto by fetching user details from the auth service.
     */
    private JobDto buildJobDto(Job job) {
        // Fetch details for both the user and the maintainer's user account
        UserDto user = authClient.getUserById(job.getUserId());

        // Find the maintainer profile to get their associated userId
        Maintainer maintainerProfile = maintainerRepository.findById(job.getMaintainerId()).orElseThrow();
        UserDto maintainerUser = authClient.getUserById(maintainerProfile.getUserId());

        JobDto.ParticipantDto userParticipant = JobDto.ParticipantDto.builder()
                .id(user.getId())
                .name(user.getFirstName() + " " + user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .build();

        JobDto.ParticipantDto maintainerParticipant = JobDto.ParticipantDto.builder()
                .id(maintainerUser.getId())
                .name(maintainerUser.getFirstName() + " " + maintainerUser.getLastName())
                .phoneNumber(maintainerUser.getPhoneNumber())
                .build();

        return JobDto.builder()
                .id(job.getId())
                .status(job.getStatus())
                .problemDescription(job.getProblemDescription())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .user(userParticipant)
                .maintainer(maintainerParticipant)
                .build();
    }

    /**
     * Private helper method to encapsulate the notification sending logic.
     */
    private void sendNotification(UUID recipientId, String message) {
        try {
            NotificationRequest notificationPayload = NotificationRequest.builder()
                    .userId(recipientId)
                    .message(message)
                    .build();
            notificationClient.sendNotification(notificationPayload);
        } catch (Exception e) {
            log.error("Failed to send notification to user {}: {}", recipientId, e.getMessage());
        }
    }
}
