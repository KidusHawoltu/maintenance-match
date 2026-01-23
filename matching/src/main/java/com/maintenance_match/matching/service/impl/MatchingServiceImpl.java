package com.maintenance_match.matching.service.impl;

import com.maintenance_match.matching.client.AuthClient;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingServiceImpl implements MatchingService {

    private final JobRepository jobRepository;
    private final MaintainerRepository maintainerRepository;
    private final AuthClient authClient;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Value("${app.kafka.topics.notification-send}")
    private String notificationTopic;

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

        // Notify User (Email + InApp)
        sendNotification(userId,
                "Match Confirmed",
                "Matched with " + maintainer.getName(),
                "job-matched-user",
                Map.of("maintainerName", maintainer.getName()),
                Set.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP));

        // Notify Maintainer (Email + InApp)
        sendNotification(maintainer.getUserId(),
                "New Job Assigned",
                "New job: " + savedJob.getProblemDescription(),
                "job-matched-maintainer",
                Map.of("problem", savedJob.getProblemDescription()),
                Set.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP));

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

        // --- NOTIFICATION LOGIC ---
        UUID customerId = updatedJob.getUserId();

        // Determine Initiator vs Receiver
        UUID receiverId = userId.equals(customerId) ? maintainerUserId : customerId;
        String terminatorRole = userId.equals(customerId) ? "customer" : "maintainer";

        String actionVerb = isCancelled ? "cancelled" : "completed";

        // 1. Notify Initiator (IN_APP ONLY)
        sendNotification(userId,
                null, // No subject needed for In-App
                "You have " + actionVerb + " the job: " + updatedJob.getProblemDescription(),
                null, // No template needed
                null,
                Set.of(NotificationChannel.IN_APP));

        // 2. Notify Receiver (EMAIL + IN_APP)
        sendNotification(receiverId,
                "Job " + (isCancelled ? "Cancelled" : "Completed"),
                "The job '" + updatedJob.getProblemDescription() + "' has been " + actionVerb + " by the " + terminatorRole + ".",
                isCancelled ? "job-cancelled" : "job-completed",
                Map.of("problem", updatedJob.getProblemDescription(), "role", terminatorRole),
                Set.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP));

        return buildJobDto(updatedJob);
    }

    @Override
    @Transactional
    public MaintainerDto updateMaintainerProfile(UUID userId, UpdateMaintainerProfileRequest request) {
        log.info("Updating profile for maintainer associated with user ID: {}", userId);

        Maintainer maintainer = maintainerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintainer profile not found for user: " + userId));

        // Update Name if provided
        if (request.getName() != null) {
            maintainer.setName(request.getName());
        }

        // Update Availability if provided
        if (request.getIsAvailable() != null) {
            maintainer.setAvailable(request.getIsAvailable());
        }

        // Update Capacity if provided
        if (request.getCapacity() != null) {
            if (request.getCapacity() < 0) {
                throw new BadRequestException("Capacity cannot be negative.");
            }
            maintainer.setCapacity(request.getCapacity());

            // Re-evaluate isAvailable based on new capacity vs active jobs
            if (maintainer.getActiveJobs() >= maintainer.getCapacity()) {
                maintainer.setAvailable(false);
            }
        }

        // Update Location if both lat and lon are provided
        if (request.getLatitude() != null && request.getLongitude() != null) {
            Point newLocation = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));
            maintainer.setLocation(newLocation);
        }

        Maintainer updatedMaintainer = maintainerRepository.save(maintainer);
        log.info("Maintainer profile updated successfully.");

        return MaintainerDto.fromEntity(updatedMaintainer);
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
    private void sendNotification(UUID recipientId, String subject, String textMessage, String template, Map<String, Object> vars, Set<NotificationChannel> channels) {
        try {
            String idStr = recipientId.toString();

            // 1. In-App Notification
            if (channels.contains(NotificationChannel.IN_APP)) {
                NotificationEvent inAppEvent = NotificationEvent.builder()
                        .eventId(UUID.randomUUID())
                        .recipientId(idStr)
                        .channel(NotificationChannel.IN_APP)
                        .message(textMessage)
                        .build();
                kafkaTemplate.send(notificationTopic, idStr, inAppEvent);
            }

            // 2. Email Notification
            if (channels.contains(NotificationChannel.EMAIL)) {
                NotificationEvent emailEvent = NotificationEvent.builder()
                        .eventId(UUID.randomUUID())
                        .recipientId(idStr)
                        .channel(NotificationChannel.EMAIL)
                        .subject(subject)
                        .template(template)
                        .variables(vars)
                        .build();
                kafkaTemplate.send(notificationTopic, idStr, emailEvent);
            }

        } catch (Exception e) {
            log.error("Failed to send notification to user {}", recipientId, e);
        }
    }
}
