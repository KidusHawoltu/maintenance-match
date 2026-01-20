package com.maintenance_match.auth.repository;

import com.maintenance_match.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUser(com.maintenance_match.auth.model.User user);

    void deleteByUser(com.maintenance_match.auth.model.User user);
}
