package com.maintenance_match.auth.service;

import com.maintenance_match.auth.dto.JwtAuthenticationResponse;
import com.maintenance_match.auth.dto.LoginRequest;
import com.maintenance_match.auth.dto.RefreshTokenRequest;
import com.maintenance_match.auth.dto.SignUpRequest;

public interface AuthenticationService {
    /**
     * Registers a new standard user.
     * @param signUpRequest DTO containing user details.
     * @return A response containing the access and refresh tokens.
     */
    JwtAuthenticationResponse signUpUser(SignUpRequest signUpRequest);

    /**
     * Registers a new maintainer who requires admin approval.
     * @param signUpRequest DTO containing maintainer details.
     * @return void as the user is not logged in immediately.
     */
    void signUpMaintainer(SignUpRequest signUpRequest);

    /**
     * Authenticates an existing user and returns JWT tokens.
     * @param loginRequest DTO containing login credentials.
     * @return A response containing the access and refresh tokens.
     */
    JwtAuthenticationResponse login(LoginRequest loginRequest);

    /**
     * Refreshes an access token using a valid refresh token.
     * @param refreshTokenRequest DTO containing the refresh token.
     * @return A response containing a new access token and the original refresh token.
     */
    JwtAuthenticationResponse refreshToken(RefreshTokenRequest refreshTokenRequest);
}
