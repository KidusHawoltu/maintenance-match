package com.maintenance_match.matching.service;

import com.maintenance_match.matching.dto.*;

import java.util.List;
import java.util.UUID;

public interface MatchingService {

    /**
     * Finds available maintainers near a given location.
     * @param latitude The latitude of the search center.
     * @param longitude The longitude of the search center.
     * @param radiusInMeters The search radius in meters.
     * @return A list of available maintainers as DTOs.
     */
    List<MaintainerDto> findNearbyMaintainers(double latitude, double longitude, double radiusInMeters);

    /**
     * Creates a new job by matching a user with a selected maintainer.
     * @param request The DTO containing the selected maintainer and problem details.
     * @param userId The ID of the user initiating the match.
     * @return The created Job as a DTO.
     */
    JobDto createJob(MatchRequestDto request, UUID userId);

    /**
     * Retrieves all jobs for a given user, identified by their role.
     * @param userId The ID of the user.
     * @param role The role of the user ('USER' or 'MAINTAINER').
     * @return A list of jobs as DTOs.
     */
    List<JobDto> getMyJobs(UUID userId, String role);

    /**
     * Terminates a job, either by completing or cancelling it.
     * @param jobId The ID of the job to terminate.
     * @param userId The ID of the user requesting the termination.
     * @param isCancelled True if the job is being cancelled, false if completed.
     * @return The updated Job as a DTO.
     */
    JobDto terminateJob(UUID jobId, UUID userId, boolean isCancelled);

    /**
     * Partially updates a maintainer's profile.
     * @param userId The ID of the user account.
     * @param request The DTO containing the fields to update.
     * @return The updated Maintainer profile.
     */
    MaintainerDto updateMaintainerProfile(UUID userId, UpdateMaintainerProfileRequest request);
}
