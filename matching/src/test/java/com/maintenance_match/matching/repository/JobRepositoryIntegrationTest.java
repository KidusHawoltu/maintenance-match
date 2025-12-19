package com.maintenance_match.matching.repository;

import com.maintenance_match.matching.AbstractIntegrationTest;
import com.maintenance_match.matching.TestUtils;
import com.maintenance_match.matching.model.Job;
import com.maintenance_match.matching.model.JobStatus;
import com.maintenance_match.matching.model.Maintainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JobRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private MaintainerRepository maintainerRepository;

    private UUID testUserId1;
    private UUID testUserId2;
    private Maintainer testMaintainer1;
    private Maintainer testMaintainer2;

    @BeforeEach
    void setUp() {
        // Clean all relevant tables before each test
        jobRepository.deleteAll();
        maintainerRepository.deleteAll();

        // --- Create mock data ---
        testUserId1 = UUID.randomUUID();
        testUserId2 = UUID.randomUUID();

        testMaintainer1 = maintainerRepository.save(Maintainer.builder()
                .name("Maintainer One")
                .userId(UUID.randomUUID())
                .isAvailable(true)
                .build());

        testMaintainer2 = maintainerRepository.save(Maintainer.builder()
                .name("Maintainer Two")
                .userId(UUID.randomUUID())
                .isAvailable(true)
                .build());

        // Create a set of jobs with different users, maintainers, and statuses
        Job job1 = Job.builder()
                .userId(testUserId1).maintainerId(testMaintainer1.getId()).status(JobStatus.ACTIVE)
                .problemDescription("Job 1").userLocation(TestUtils.createPoint(1, 1)).build();

        Job job2 = Job.builder()
                .userId(testUserId1).maintainerId(testMaintainer2.getId()).status(JobStatus.COMPLETED)
                .problemDescription("Job 2").userLocation(TestUtils.createPoint(2, 2)).build();

        Job job3 = Job.builder()
                .userId(testUserId2).maintainerId(testMaintainer1.getId()).status(JobStatus.ACTIVE)
                .problemDescription("Job 3").userLocation(TestUtils.createPoint(3, 3)).build();

        jobRepository.saveAll(List.of(job1, job2, job3));
    }

    @Test
    void findByUserId_shouldReturnAllJobsForUser() {
        // When: Find ALL jobs for User 1, regardless of status
        List<Job> jobsForUser1 = jobRepository.findByUserId(testUserId1);

        // Then: Should find Job 1 (ACTIVE) and Job 2 (COMPLETED)
        assertThat(jobsForUser1).hasSize(2);
        assertThat(jobsForUser1)
                .extracting(Job::getProblemDescription)
                .containsExactlyInAnyOrder("Job 1", "Job 2");
    }

    @Test
    void findByMaintainerId_shouldReturnAllJobsForMaintainer() {
        // When: Find ALL jobs for Maintainer 1, regardless of status
        List<Job> jobsForMaintainer1 = jobRepository.findByMaintainerId(testMaintainer1.getId());

        // Then: Should find Job 1 (ACTIVE, from User 1) and Job 3 (ACTIVE, from User 2)
        assertThat(jobsForMaintainer1).hasSize(2);
        assertThat(jobsForMaintainer1)
                .extracting(Job::getProblemDescription)
                .containsExactlyInAnyOrder("Job 1", "Job 3");
    }

    @Test
    void findByUserIdAndStatus_shouldReturnCorrectJobs() {
        // When: Find all ACTIVE jobs for User 1
        List<Job> activeJobsForUser1 = jobRepository.findByUserIdAndStatus(testUserId1, JobStatus.ACTIVE);

        // Then: Should find only Job 1
        assertThat(activeJobsForUser1).hasSize(1);
        assertThat(activeJobsForUser1.get(0).getProblemDescription()).isEqualTo("Job 1");
    }

    @Test
    void findByMaintainerIdAndStatus_shouldReturnCorrectJobs() {
        // When: Find all ACTIVE jobs for Maintainer 1
        List<Job> activeJobsForMaintainer1 = jobRepository.findByMaintainerIdAndStatus(testMaintainer1.getId(), JobStatus.ACTIVE);

        // Then: Should find Job 1 and Job 3
        assertThat(activeJobsForMaintainer1).hasSize(2);
        assertThat(activeJobsForMaintainer1)
                .extracting(Job::getProblemDescription)
                .containsExactlyInAnyOrder("Job 1", "Job 3");
    }

    @Test
    void findByUserIdAndStatusIn_shouldReturnJobsWithAnyOfTheGivenStatuses() {
        // When: Find all jobs for User 1 that are either COMPLETED or CANCELLED
        List<Job> pastJobsForUser1 = jobRepository.findByUserIdAndStatusIn(
                testUserId1, List.of(JobStatus.COMPLETED, JobStatus.CANCELLED_BY_USER));

        // Then: Should find only Job 2
        assertThat(pastJobsForUser1).hasSize(1);
        assertThat(pastJobsForUser1.get(0).getProblemDescription()).isEqualTo("Job 2");
    }

    @Test
    void findByMaintainerIdAndStatusIn_shouldReturnEmptyListWhenNoMatch() {
        // When: Find all CANCELLED jobs for Maintainer 2
        List<Job> cancelledJobsForMaintainer2 = jobRepository.findByMaintainerIdAndStatusIn(
                testMaintainer2.getId(), List.of(JobStatus.CANCELLED_BY_USER, JobStatus.CANCELLED_BY_MAINTAINER));

        // Then: Should find none
        assertThat(cancelledJobsForMaintainer2).isEmpty();
    }
}
