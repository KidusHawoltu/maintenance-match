package com.maintenance_match.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token; // The actual refresh token string (we'll use a UUID for this)

    @Column(nullable = false)
    private Instant expiryDate;

    @OneToOne // Each refresh token is owned by one user
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;
}
