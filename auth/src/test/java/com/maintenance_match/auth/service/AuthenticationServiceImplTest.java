package com.maintenance_match.auth.service;

import com.maintenance_match.auth.dto.*;
import com.maintenance_match.auth.exception.BadRequestException;
import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.RefreshToken;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.UserRepository;
import com.maintenance_match.auth.service.impl.AuthenticationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private final String NOTIF_TOPIC = "notification-topic";
    private SignUpRequest signUpRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authenticationService, "notificationTopic", NOTIF_TOPIC);

        signUpRequest = SignUpRequest.builder()
                .firstName("Kidus")
                .lastName("Asebe")
                .email("kidus@example.com")
                .phoneNumber("0911223344")
                .password("securePassword")
                .build();
    }

    @Test
    void signUpUser_Success_ShouldReturnTokensAndSendNotifications() {
        // Given
        when(userRepository.findByEmail(signUpRequest.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any())).thenReturn(RefreshToken.builder().token("refresh-token").build());

        // When
        JwtAuthenticationResponse response = authenticationService.signUpUser(signUpRequest);

        // Then
        // 1. Verify User persistence state
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.getApprovalStatus()).isNull();

        // 2. Verify Kafka Notifications (Helper method calls send twice)
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(kafkaTemplate, times(2)).send(eq(NOTIF_TOPIC), anyString(), eventCaptor.capture());

        List<NotificationEvent> events = eventCaptor.getAllValues();
        assertThat(events).extracting(NotificationEvent::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
        assertThat(events.get(0).getTemplate()).isEqualTo("welcome-email");

        // 3. Verify Response
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void signUpUser_DuplicateEmail_ShouldThrowException() {
        // Given
        when(userRepository.findByEmail(signUpRequest.getEmail())).thenReturn(Optional.of(new User()));

        // When / Then
        assertThatThrownBy(() -> authenticationService.signUpUser(signUpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email address already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    void signUpMaintainer_Success_ShouldBeInactiveAndPending() {
        // Given
        when(userRepository.findByEmail(signUpRequest.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        // When
        authenticationService.signUpMaintainer(signUpRequest);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getRole()).isEqualTo(Role.MAINTAINER);
        assertThat(savedUser.isActive()).isFalse();
        assertThat(savedUser.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);

        // Verify Notifications sent (Email + InApp)
        verify(kafkaTemplate, times(2)).send(eq(NOTIF_TOPIC), anyString(), any(NotificationEvent.class));

        // Ensure NO tokens are generated (Maintainer can't log in yet)
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void login_Success_ShouldReturnTokens() {
        // Given
        LoginRequest loginRequest = new LoginRequest("kidus@example.com", "password");
        User existingUser = User.builder().email("kidus@example.com").build();

        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(existingUser)).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(existingUser)).thenReturn(RefreshToken.builder().token("new-refresh-token").build());

        // When
        JwtAuthenticationResponse response = authenticationService.login(loginRequest);

        // Then
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void refreshToken_Success_ShouldRotateAndReturnNewTokens() {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("old-token");
        User user = User.builder().id(UUID.randomUUID()).build();
        RefreshToken rotatedToken = RefreshToken.builder().token("new-rotated-token").user(user).build();

        when(refreshTokenService.rotateRefreshToken("old-token")).thenReturn(rotatedToken);
        when(jwtService.generateToken(user)).thenReturn("brand-new-access-token");

        // When
        JwtAuthenticationResponse response = authenticationService.refreshToken(request);

        // Then
        assertThat(response.getAccessToken()).isEqualTo("brand-new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-rotated-token");
    }
}
