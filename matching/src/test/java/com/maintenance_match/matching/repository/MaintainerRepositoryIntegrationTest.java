package com.maintenance_match.matching.repository;

import com.maintenance_match.matching.AbstractIntegrationTest;
import com.maintenance_match.matching.TestUtils;
import com.maintenance_match.matching.model.Maintainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class MaintainerRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MaintainerRepository maintainerRepository;

    @BeforeEach
    void setUp() {
        maintainerRepository.deleteAll();
    }

    @Test
    void findByUserId_whenMaintainerExists_shouldReturnMaintainer() {
        // Given
        UUID targetUserId = UUID.randomUUID();
        Maintainer maintainer = Maintainer.builder()
                .name("Target Maintainer")
                .userId(targetUserId) // Use a specific userId
                .isAvailable(true)
                .build();

        maintainerRepository.save(Maintainer.builder().name("Other").userId(UUID.randomUUID()).build());
        maintainerRepository.save(maintainer);

        // When
        Optional<Maintainer> foundMaintainer = maintainerRepository.findByUserId(targetUserId);

        // Then
        assertThat(foundMaintainer).isPresent();
        assertThat(foundMaintainer.get().getName()).isEqualTo("Target Maintainer");
        assertThat(foundMaintainer.get().getUserId()).isEqualTo(targetUserId);
    }

    @Test
    void findByUserId_whenMaintainerDoesNotExist_shouldReturnEmpty() {
        // When
        Optional<Maintainer> foundMaintainer = maintainerRepository.findByUserId(UUID.randomUUID());

        // Then
        assertThat(foundMaintainer).isNotPresent();
    }

    @Test
    void findAvailableMaintainersWithinRadius_shouldReturnCorrectMaintainers() {
        // --- Given ---

        // A location in Addis Ababa, Ethiopia (St. George's Cathedral)
        Point searchCenter = TestUtils.createPoint(38.7542, 9.0325);

        // Maintainer 1: Nearby and available (approx 1.1 km away)
        Maintainer nearbyAvailable = Maintainer.builder()
                .name("Kirubel's Garage")
                .userId(UUID.randomUUID())
                .isAvailable(true)
                .location(TestUtils.createPoint(38.7630, 9.0270))
                .build();

        // Maintainer 2: Nearby but NOT available
        Maintainer nearbyUnavailable = Maintainer.builder()
                .name("Hermela's Auto")
                .userId(UUID.randomUUID())
                .isAvailable(false)
                .location(TestUtils.createPoint(38.7631, 9.0271))
                .build();

        // Maintainer 3: Too far away but available (approx 15 km away)
        Maintainer farawayAvailable = Maintainer.builder()
                .name("Kidus's Shop")
                .userId(UUID.randomUUID())
                .isAvailable(true)
                .location(TestUtils.createPoint(38.8500, 9.0000))
                .build();

        maintainerRepository.saveAll(List.of(nearbyAvailable, nearbyUnavailable, farawayAvailable));

        // --- When ---

        // Search within a 2000 meter (2 km) radius
        double searchRadius = 2000;
        List<Maintainer> foundMaintainers = maintainerRepository.findAvailableMaintainersWithinRadius(searchCenter, searchRadius);

        // --- Then ---

        // We should find exactly one maintainer
        assertThat(foundMaintainers).hasSize(1);
        // And it should be the nearby, available one
        assertThat(foundMaintainers.get(0).getName()).isEqualTo("Kirubel's Garage");
    }

    @Test
    void findAvailableMaintainersWithinRadius_whenNoneAvailable_shouldReturnEmptyList() {
        // --- Given ---
        Point searchCenter = TestUtils.createPoint(38.7542, 9.0325);

        Maintainer nearbyUnavailable = Maintainer.builder()
                .name("Hermela's Auto")
                .userId(UUID.randomUUID())
                .isAvailable(false)
                .location(TestUtils.createPoint(38.7631, 9.0271))
                .build();
        maintainerRepository.save(nearbyUnavailable);

        // --- When ---
        double searchRadius = 2000;
        List<Maintainer> foundMaintainers = maintainerRepository.findAvailableMaintainersWithinRadius(searchCenter, searchRadius);

        // --- Then ---
        assertThat(foundMaintainers).isEmpty();
    }

    @Test
    void findAvailableMaintainersWithinRadius_whenAvailableButOutOfRange_shouldReturnEmptyList() {
        // --- Given ---
        Point searchCenter = TestUtils.createPoint(38.7542, 9.0325); // St. George's Cathedral

        Maintainer farawayAvailable = Maintainer.builder()
                .name("Faraway but Available Shop")
                .userId(UUID.randomUUID())
                .isAvailable(true)
                .location(TestUtils.createPoint(38.8500, 9.0000))
                .build();

        maintainerRepository.save(farawayAvailable);

        // --- When ---
        double searchRadius = 2000;
        List<Maintainer> foundMaintainers = maintainerRepository.findAvailableMaintainersWithinRadius(searchCenter, searchRadius);

        // --- Then ---
        assertThat(foundMaintainers).isEmpty();
    }

    @Test
    void findAvailableMaintainersWithinRadius_whenNearbyAndAvailableButAtCapacity_shouldReturnEmptyList() {
        // --- Given ---
        Point searchCenter = TestUtils.createPoint(38.7542, 9.0325);

        Maintainer atCapacityMaintainer = Maintainer.builder()
                .name("Busy but Available Garage")
                .userId(UUID.randomUUID())
                .isAvailable(true)
                .location(TestUtils.createPoint(38.7630, 9.0270))
                .capacity(2)
                .activeJobs(2)
                .build();

        maintainerRepository.save(atCapacityMaintainer);

        // --- When ---
        double searchRadius = 2000;
        List<Maintainer> foundMaintainers = maintainerRepository.findAvailableMaintainersWithinRadius(searchCenter, searchRadius);

        // --- Then ---
        assertThat(foundMaintainers).isEmpty();
    }
}
