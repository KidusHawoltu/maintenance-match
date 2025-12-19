package com.maintenance_match.matching.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "maintainers")
public class Maintainer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    private boolean isAvailable;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Builder.Default // Default capacity to 1 unless specified otherwise
    @Column(nullable = false)
    private Integer capacity = 1;

    @Builder.Default // Default active jobs to 0
    @Column(nullable = false)
    private Integer activeJobs = 0;
}
