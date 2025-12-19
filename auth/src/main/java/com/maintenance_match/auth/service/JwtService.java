package com.maintenance_match.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import java.security.PublicKey;

public interface JwtService {
    /**
     * Extracts the username (email) from a JWT token.
     * @param token The JWT token.
     * @return The username.
     */
    String extractUsername(String token);

    /**
     * Generates a new access token for a user.
     * @param userDetails The user details object.
     * @return The generated JWT token string.
     */
    String generateToken(UserDetails userDetails);

    /**
     * Checks if a token is valid for a given user.
     * @param token The JWT token.
     * @param userDetails The user details to validate against.
     * @return True if the token is valid, false otherwise.
     */
    boolean isTokenValid(String token, UserDetails userDetails);

    /**
     * Exposes the public key for other services (like the API Gateway) to use for validation.
     * @return The public key.
     */
    PublicKey getPublicKey();
}
