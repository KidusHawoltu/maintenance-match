package com.maintenance_match.matching.controller;

import com.maintenance_match.matching.dto.JobDto;
import com.maintenance_match.matching.dto.MaintainerDto;
import com.maintenance_match.matching.dto.MatchRequestDto;
import com.maintenance_match.matching.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
@Tag(name = "Matching & Jobs", description = "Endpoints for finding maintainers and managing jobs")
@SecurityRequirement(name = "bearerAuth")
public class MatchingController {

    private final MatchingService matchingService;

    @GetMapping("/maintainers/nearby")
    @Operation(summary = "Find nearby available maintainers")
    public ResponseEntity<List<MaintainerDto>> findNearbyMaintainers(
            @Parameter(description = "Latitude of the search center", required = true, example = "9.0325")
            @RequestParam double latitude,
            @Parameter(description = "Longitude of the search center", required = true, example = "38.7542")
            @RequestParam double longitude,
            @Parameter(description = "Search radius in meters", example = "5000")
            @RequestParam(defaultValue = "5000") double radius) {

        List<MaintainerDto> maintainers = matchingService.findNearbyMaintainers(latitude, longitude, radius);
        return ResponseEntity.ok(maintainers);
    }

    @PostMapping("/jobs")
    @Operation(summary = "Create a new job (match with a maintainer)", description = "Initiated by a user after selecting a maintainer from the search results.")
    public ResponseEntity<JobDto> createJob(
            @Valid @RequestBody MatchRequestDto matchRequest,
            @Parameter(hidden = true) @RequestHeader("X-User-ID") String userIdHeader) {

        UUID userId = UUID.fromString(userIdHeader);
        JobDto createdJob = matchingService.createJob(matchRequest, userId);
        return new ResponseEntity<>(createdJob, HttpStatus.CREATED);
    }

    @GetMapping("/jobs/my-jobs")
    @Operation(summary = "Get all jobs for the current user", description = "Returns a list of all active and past jobs for the authenticated user (works for both USERS and MAINTAINERS).")
    public ResponseEntity<List<JobDto>> getMyJobs(
            @Parameter(hidden = true) @RequestHeader("X-User-ID") String userIdHeader,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String userRole) {

        UUID userId = UUID.fromString(userIdHeader);
        List<JobDto> jobs = matchingService.getMyJobs(userId, userRole);
        return ResponseEntity.ok(jobs);
    }

    @PostMapping("/jobs/{jobId}/complete")
    @Operation(summary = "Mark a job as completed", description = "Can be initiated by either the user or the maintainer in the job.")
    public ResponseEntity<JobDto> completeJob(
            @PathVariable UUID jobId,
            @Parameter(hidden = true) @RequestHeader("X-User-ID") String userIdHeader) {

        UUID userId = UUID.fromString(userIdHeader);
        JobDto updatedJob = matchingService.terminateJob(jobId, userId, false); // isCancelled = false
        return ResponseEntity.ok(updatedJob);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Cancel an active job", description = "Can be initiated by either the user or the maintainer in the job.")
    public ResponseEntity<JobDto> cancelJob(
            @PathVariable UUID jobId,
            @Parameter(hidden = true) @RequestHeader("X-User-ID") String userIdHeader) {

        UUID userId = UUID.fromString(userIdHeader);
        JobDto updatedJob = matchingService.terminateJob(jobId, userId, true); // isCancelled = true
        return ResponseEntity.ok(updatedJob);
    }
}
