package com.maintenance_match.auth.service.impl;

import com.maintenance_match.auth.dto.*;
import com.maintenance_match.auth.exception.BadRequestException;
import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.RefreshToken;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.UserRepository;
import com.maintenance_match.auth.service.AuthenticationService;
import com.maintenance_match.auth.service.JwtService;
import com.maintenance_match.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.notification-send}")
    private String notificationTopic;

    @Override
    public JwtAuthenticationResponse signUpUser(SignUpRequest signUpRequest) {
        if (userRepository.findByEmail(signUpRequest.getEmail()).isPresent()) {
            throw new BadRequestException("Email address already in use.");
        }

        User user = User.builder()
                .firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .email(signUpRequest.getEmail())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                .role(Role.USER) // Assign USER role
                .isActive(true) // Active immediately
                .build();
        userRepository.save(user);

        // A regular user is logged in immediately after signing up
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // --- SEND WELCOME NOTIFICATION ---
        sendNotification(
                user.getId(),
                "Welcome to MaintenanceMatch!",
                "Welcome " + user.getFirstName() + ", your account has been created successfully.",
                "welcome-email", // We will create this template later
                Map.of("name", user.getFirstName())
        );

        return JwtAuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    @Override
    public void signUpMaintainer(SignUpRequest signUpRequest) {
        if (userRepository.findByEmail(signUpRequest.getEmail()).isPresent()) {
            throw new BadRequestException("Email address already in use.");
        }

        User user = User.builder()
                .firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .email(signUpRequest.getEmail())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                .role(Role.MAINTAINER) // Assign MAINTAINER role
                .isActive(false) // Inactive until approved
                .approvalStatus(ApprovalStatus.PENDING) // Awaiting approval
                .build();
        userRepository.save(user);

        // --- SEND APPLICATION RECEIVED NOTIFICATION ---
        sendNotification(
                user.getId(),
                "Application Received",
                "Your maintainer application has been received and is pending approval.",
                "maintainer-signup",
                Map.of("name", user.getFirstName())
        );
    }

    @Override
    public JwtAuthenticationResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return JwtAuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    @Override
    public JwtAuthenticationResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        RefreshToken refreshToken = refreshTokenService.rotateRefreshToken(refreshTokenRequest.getRefreshToken());
        User user = refreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user);

        return JwtAuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    // --- HELPER METHOD TO SEND SPLIT EVENTS ---
    private void sendNotification(UUID userId, String subject, String textMessage, String template, Map<String, Object> vars) {
        String recipientId = userId.toString();

        // 1. In-App Notification (DB Persist)
        NotificationEvent inAppEvent = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .recipientId(recipientId)
                .channel(NotificationChannel.IN_APP)
                .message(textMessage)
                .build();
        kafkaTemplate.send(notificationTopic, recipientId, inAppEvent);

        // 2. Email Notification
        NotificationEvent emailEvent = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .recipientId(recipientId)
                .channel(NotificationChannel.EMAIL)
                .subject(subject)
                .template(template)
                .variables(vars)
                .build();
        kafkaTemplate.send(notificationTopic, recipientId, emailEvent);
    }
}
