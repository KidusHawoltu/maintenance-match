package com.maintenance_match.matching.repository;

import com.maintenance_match.matching.model.Maintainer;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface MaintainerRepository extends JpaRepository<Maintainer, UUID> {

    /**
     * Finds all available maintainers within a given radius of a point.
     * This uses the PostGIS ST_DWithin function, which is highly efficient.
     * @param point The center point for the search.
     * @param radius The search radius in meters.
     * @return A list of maintainers within the radius.
     */
    @Query(value = "SELECT m.* FROM maintainers m " +
            "WHERE m.is_available = true " +
            "AND m.active_jobs < m.capacity " +
            "AND ST_DWithin(CAST(m.location AS geography), CAST(:point AS geography), :radius)",
            nativeQuery = true)
    List<Maintainer> findAvailableMaintainersWithinRadius(
            @Param("point") Point point,
            @Param("radius") double radius
    );

    /**
     * Finds a maintainer profile by their associated user account ID.
     * @param userId The UUID of the user account.
     * @return An Optional containing the Maintainer if found.
     */
    Optional<Maintainer> findByUserId(UUID userId);
}
