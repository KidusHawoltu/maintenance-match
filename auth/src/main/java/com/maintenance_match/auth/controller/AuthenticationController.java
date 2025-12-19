package com.maintenance_match.auth.controller;

import com.maintenance_match.auth.dto.*; // Import your DTOs
import com.maintenance_match.auth.service.AuthenticationService;
import com.maintenance_match.auth.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user sign-up, login, and token management")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;

    @Operation(summary = "Register a new standard user", description = "Creates a new user account and returns access and refresh tokens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = JwtAuthenticationResponse.class)) }),
            @ApiResponse(responseCode = "400", description = "Invalid input, such as an already existing email or invalid data",
                    content = @Content)
    })
    @PostMapping("/signup/user")
    public ResponseEntity<JwtAuthenticationResponse> signupUser(@Valid @RequestBody SignUpRequest signUpRequest) {
        return ResponseEntity.ok(authenticationService.signUpUser(signUpRequest));
    }

    @PostMapping("/signup/maintainer")
    @Operation(summary = "Register a new maintainer (requires admin approval)")
    @ApiResponse(responseCode = "202", description = "Registration request accepted and is pending approval")
    public ResponseEntity<Void> signupMaintainer(@Valid @RequestBody SignUpRequest signUpRequest) {
        authenticationService.signUpMaintainer(signUpRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "Authenticate an existing user", description = "Logs in a user with email and password, returning access and refresh tokens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User logged in successfully",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = JwtAuthenticationResponse.class)) }),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<JwtAuthenticationResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authenticationService.login(loginRequest));
    }

    @Operation(summary = "Refresh an access token", description = "Provides a new access token and a new rotated refresh token in exchange for a valid refresh token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = JwtAuthenticationResponse.class)) }),
            @ApiResponse(responseCode = "403", description = "Invalid or expired refresh token", content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthenticationResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        return ResponseEntity.ok(authenticationService.refreshToken(refreshTokenRequest));
    }

    @Operation(summary = "Get the public key for JWT validation", description = "Exposes the RSA public key in Base64 format for external services to validate JWT signatures.")
    @ApiResponse(responseCode = "200", description = "Public key retrieved successfully")
    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        PublicKey publicKey = jwtService.getPublicKey();
        String base64PublicKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return ResponseEntity.ok(Map.of("publicKey", base64PublicKey));
    }
}
