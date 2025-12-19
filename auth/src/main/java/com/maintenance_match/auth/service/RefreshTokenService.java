package com.maintenance_match.auth.service;

import com.maintenance_match.auth.model.RefreshToken;
import com.maintenance_match.auth.model.User;

import java.util.Optional;

public interface RefreshTokenService {
    /**
     * Creates and persists a new refresh token for a user.
     * @param user The user to create the token for.
     * @return The created RefreshToken entity.
     */
    RefreshToken createRefreshToken(User user);

    /**
     * Finds a refresh token by its token string.
     * @param token The token string.
     * @return An Optional containing the RefreshToken if found.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Verifies that a refresh token has not expired. Deletes it if it has.
     * @param token The RefreshToken entity.
     * @return The verified RefreshToken entity.
     * @throws com.maintenance_match.auth.exception.TokenRefreshException if the token is expired.
     */
    RefreshToken verifyExpiration(RefreshToken token);

    /**
     * Deletes an old refresh token and creates a new one for the same user.
     * @param oldToken The token string of the refresh token to be rotated.
     * @return The newly created RefreshToken entity.
     * @throws com.maintenance_match.auth.exception.TokenRefreshException if the old token is not found or is expired.
     */
    RefreshToken rotateRefreshToken(String oldToken);
}
