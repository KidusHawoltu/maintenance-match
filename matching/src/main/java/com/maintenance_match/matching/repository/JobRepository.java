package com.maintenance_match.matching.repository;

import com.maintenance_match.matching.model.Job;
import com.maintenance_match.matching.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByUserId(UUID userId);
    List<Job> findByMaintainerId(UUID maintainerId);

    // Find all jobs for a user with a specific status
    List<Job> findByUserIdAndStatus(UUID userId, JobStatus status);

    // Find all jobs for a maintainer with a specific status
    List<Job> findByMaintainerIdAndStatus(UUID maintainerId, JobStatus status);

    // Find all jobs for a user with any of a list of statuses (e.g., all "completed" or "cancelled" jobs)
    List<Job> findByUserIdAndStatusIn(UUID userId, List<JobStatus> statuses);

    // Find all jobs for a maintainer with any of a list of statuses
    List<Job> findByMaintainerIdAndStatusIn(UUID maintainerId, List<JobStatus> statuses);
}
